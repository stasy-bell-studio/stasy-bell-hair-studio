
import os
from uuid import UUID

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.auth import make_access_token, require_owner, verify_login
from app.calendar_api import router as calendar_router
from app.public_booking import router as public_router, rate_limit
from app.messenger_webhooks import router as messenger_router
from app.finance_api import router as finance_router
from app.db import get_db
from app.models import Client, MasterAccount, Service
from app.web_origins import trusted_web_origins

app = FastAPI(title="Stasy Bell OS V2 API", version="0.1.0", docs_url=None, redoc_url=None)
allowed_origins = trusted_web_origins(os.environ.get("STASY_ALLOWED_WEB_ORIGINS", ""))
if allowed_origins:
    app.add_middleware(CORSMiddleware, allow_origins=allowed_origins,
                       allow_credentials=False, allow_methods=["GET", "POST"],
                       allow_headers=["Content-Type"], max_age=600)


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


@app.get("/health")
def health():
    return {"status": "ok", "version": "v2"}


@app.get("/ready")
def ready(db: Session = Depends(get_db)):
    db.execute(text("SELECT 1"))
    return {"status": "ready"}


@app.post("/auth/login")
def login(data: Login, request: Request, db: Session = Depends(get_db)):
    rate_limit(request, "owner_login", max_events=10, seconds=600)
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







class UpdateService(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    variant: str | None = Field(default=None, max_length=200)
    price_kopecks: int | None = Field(default=None, ge=0)
    duration_minutes: int | None = Field(default=None, gt=0, le=1440)
    buffer_minutes: int | None = Field(default=None, ge=0, le=1440)
    active: bool | None = None


@app.patch("/owner/services/{service_id}")
def update_service(
    service_id: UUID, data: UpdateService,
    db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    service = db.get(Service, service_id)
    if service is None:
        raise HTTPException(status_code=404, detail="Service not found")
    if not data.model_fields_set:
        raise HTTPException(status_code=422, detail="No changes supplied")
    changes = data.model_dump(exclude_unset=True)
    for required in ("name", "duration_minutes", "buffer_minutes", "active"):
        if required in changes and changes[required] is None:
            raise HTTPException(status_code=422, detail="Required service fields cannot be empty")
    for key, value in changes.items():
        setattr(service, key, value)
    if service.active and not service.duration_minutes:
        raise HTTPException(status_code=422, detail="Active service requires duration")
    db.commit()
    return {"id": service.id, "active": service.active}

@app.get("/owner/clients")
def list_clients(
    db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    rows = db.scalars(select(Client).order_by(Client.name, Client.created_at)).all()
    return {"clients": [{"id": item.id, "name": item.name, "phone": item.phone}
                        for item in rows]}


@app.get("/owner/services")
def list_services(
    db: Session = Depends(get_db), owner: MasterAccount = Depends(require_owner),
):
    rows = db.scalars(select(Service).order_by(Service.name, Service.variant)).all()
    return {"services": [{
        "id": item.id, "name": item.name, "variant": item.variant,
        "price_kopecks": item.price_kopecks,
        "duration_minutes": item.duration_minutes,
        "buffer_minutes": item.buffer_minutes, "active": item.active,
    } for item in rows]}


app.include_router(calendar_router)
app.include_router(public_router)
app.include_router(messenger_router)
app.include_router(finance_router)
