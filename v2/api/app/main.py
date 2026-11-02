
from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from app.auth import make_access_token, require_owner, verify_login
from app.calendar_api import router as calendar_router
from app.db import get_db
from app.models import Client, MasterAccount, Service

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




app.include_router(calendar_router)
