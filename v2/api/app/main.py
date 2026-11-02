from datetime import datetime, timedelta
from uuid import UUID

from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel, Field, model_validator
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.auth import make_access_token, require_owner, verify_login
from app.booking import persist_entry
from app.db import get_db
from app.models import CalendarEntry, Client, MasterAccount, Service

app = FastAPI(title="Stasy Bell OS V2 API", version="0.1.0", docs_url=None, redoc_url=None)


class Login(BaseModel):
    username: str
    password: str


class NewClient(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    phone: str | None = Field(default=None, max_length=40)


class NewService(BaseModel):
    code: str = Field(min_length=1, max_length=120)
    name: str = Field(min_length=1, max_length=200)
    variant: str | None = Field(default=None, max_length=200)
    price_kopecks: int | None = Field(default=None, ge=0)
    duration_minutes: int | None = Field(default=None, gt=0, le=1440)
    buffer_minutes: int = Field(default=0, ge=0, le=1440)
    active: bool = False


class NewAppointment(BaseModel):
    client_id: UUID
    service_id: UUID
    starts_at: datetime
    source: str = Field(pattern="^(phone|telegram_manual|vk_manual|other_manual)$")

    @model_validator(mode="after")
    def validate_timezone(self):
        if self.starts_at.tzinfo is None or self.starts_at.utcoffset() is None:
            raise ValueError("Timezone-aware start required")
        return self


class NewBlock(BaseModel):
    starts_at: datetime
    ends_at: datetime

    @model_validator(mode="after")
    def validate_interval(self):
        if (self.starts_at.tzinfo is None or self.ends_at.tzinfo is None
                or self.starts_at.utcoffset() is None or self.ends_at.utcoffset() is None
                or self.ends_at <= self.starts_at):
            raise ValueError("Valid timezone-aware interval required")
        return self


@app.get("/health")
def health():
    return {"status": "ok", "version": "v2"}


@app.get("/ready")
def ready(db: Session = Depends(get_db)):
    db.execute(text("SELECT 1"))
    return {"status": "ready"}


@app.post("/auth/login")
def login(data: Login, db: Session = Depends(get_db)):
    owner = verify_login(db, data.username, data.password)
    if owner is None:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    return {"access_token": make_access_token(owner), "token_type": "bearer", "expires_in": 1800}


@app.post("/owner/clients", status_code=201)
def create_client(
    data: NewClient, db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    client = Client(name=data.name, phone=data.phone)
    db.add(client)
    db.commit()
    return {"id": client.id}


@app.post("/owner/services", status_code=201)
def create_service(
    data: NewService, db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    if db.scalar(select(Service.id).where(Service.code == data.code)):
        raise HTTPException(status_code=409, detail="Service code already exists")
    service = Service(**data.model_dump())
    db.add(service)
    db.commit()
    return {"id": service.id}


@app.post("/owner/calendar/appointments", status_code=201)
def create_appointment(
    data: NewAppointment, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    client = db.get(Client, data.client_id)
    service = db.get(Service, data.service_id)
    if client is None or service is None or not service.active or not service.duration_minutes:
        raise HTTPException(status_code=422, detail="Unknown client or inactive/unconfigured service")
    end = data.starts_at + timedelta(minutes=service.duration_minutes)
    occupied_until = end + timedelta(minutes=service.buffer_minutes)
    entry = CalendarEntry(
        kind="appointment", client_id=client.id, service_id=service.id, source=data.source,
        status="confirmed", starts_at=data.starts_at, ends_at=end,
        occupied_until=occupied_until,
    )
    return {"id": persist_entry(db, entry, actor=str(owner.id)), "status": "confirmed"}


@app.post("/owner/calendar/blocks", status_code=201)
def create_block(
    data: NewBlock, db: Session = Depends(get_db),
    owner: MasterAccount = Depends(require_owner),
):
    entry = CalendarEntry(
        kind="block", source="master", status="blocked", starts_at=data.starts_at,
        ends_at=data.ends_at, occupied_until=data.ends_at,
    )
    return {"id": persist_entry(db, entry, actor=str(owner.id)), "status": "blocked"}
