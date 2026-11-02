from datetime import date, datetime, time
from uuid import UUID, uuid4

from sqlalchemy import (
    Boolean, CheckConstraint, Date, DateTime, ForeignKey, Integer, String,
    Text, Time, UniqueConstraint, func,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID as PGUUID, ExcludeConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from sqlalchemy.sql import text


class Base(DeclarativeBase):
    pass


class MasterAccount(Base):
    __tablename__ = "master_accounts"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    username: Mapped[str] = mapped_column(String(80), unique=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    enabled: Mapped[bool] = mapped_column(Boolean, default=True, server_default=text("true"))


class Client(Base):
    __tablename__ = "clients"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    phone: Mapped[str | None] = mapped_column(String(40))
    preferred_channel: Mapped[str | None] = mapped_column(String(16))
    telegram_chat_id: Mapped[str | None] = mapped_column(String(100))
    vk_user_id: Mapped[str | None] = mapped_column(String(100))
    notes: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (
        CheckConstraint("preferred_channel IS NULL OR preferred_channel IN ('telegram', 'vk')", name="client_channel"),
    )


class Service(Base):
    __tablename__ = "services"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    code: Mapped[str] = mapped_column(String(120), unique=True)
    name: Mapped[str] = mapped_column(String(200))
    variant: Mapped[str | None] = mapped_column(String(200))
    price_kopecks: Mapped[int | None] = mapped_column(Integer)
    duration_minutes: Mapped[int | None] = mapped_column(Integer)
    buffer_minutes: Mapped[int] = mapped_column(Integer, default=0, server_default=text("0"))
    active: Mapped[bool] = mapped_column(Boolean, default=False, server_default=text("false"))
    __table_args__ = (
        CheckConstraint("price_kopecks IS NULL OR price_kopecks >= 0", name="nonnegative_price"),
        CheckConstraint("duration_minutes IS NULL OR duration_minutes > 0", name="positive_duration"),
        CheckConstraint("buffer_minutes >= 0", name="nonnegative_buffer"),
    )


class WorkingWindow(Base):
    __tablename__ = "working_windows"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    day: Mapped[date] = mapped_column(Date, nullable=False)
    starts_at: Mapped[time] = mapped_column(Time, nullable=False)
    ends_at: Mapped[time] = mapped_column(Time, nullable=False)
    __table_args__ = (
        CheckConstraint("ends_at > starts_at", name="valid_working_window"),
        UniqueConstraint("day", "starts_at", "ends_at", name="uq_working_window"),
    )


class CalendarEntry(Base):
    """Appointments and time blocks share one PostgreSQL exclusion constraint."""
    __tablename__ = "calendar_entries"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    kind: Mapped[str] = mapped_column(String(16), nullable=False)
    client_id: Mapped[UUID | None] = mapped_column(PGUUID(as_uuid=True), ForeignKey("clients.id", ondelete="RESTRICT"))
    service_id: Mapped[UUID | None] = mapped_column(PGUUID(as_uuid=True), ForeignKey("services.id", ondelete="RESTRICT"))
    source: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[str] = mapped_column(String(24), nullable=False)
    starts_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ends_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    occupied_until: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    hold_until: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    hold_access_sha256: Mapped[str | None] = mapped_column(String(64))
    token_count: Mapped[int] = mapped_column(Integer, default=0, server_default=text("0"))
    duration_minutes: Mapped[int | None] = mapped_column(Integer)
    quoted_price_kopecks: Mapped[int | None] = mapped_column(Integer)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (
        CheckConstraint("ends_at > starts_at AND occupied_until >= ends_at", name="valid_calendar_times"),
        CheckConstraint("kind IN ('appointment','block')", name="valid_calendar_kind"),
        CheckConstraint("token_count >= 0 AND token_count <= 3", name="valid_token_count"),
        CheckConstraint("duration_minutes IS NULL OR duration_minutes > 0", name="valid_visit_duration"),
        CheckConstraint("quoted_price_kopecks IS NULL OR quoted_price_kopecks >= 0", name="valid_quoted_price"),
        CheckConstraint("status IN ('pending_channel','confirmed','expired','cancelled','completed','no_show','blocked')", name="valid_calendar_status"),
        CheckConstraint(
            "(kind = 'block' AND status = 'blocked' AND client_id IS NULL AND service_id IS NULL AND hold_until IS NULL) "
            "OR (kind = 'appointment' AND client_id IS NOT NULL AND service_id IS NOT NULL "
            "AND status <> 'blocked' AND (status <> 'pending_channel' OR hold_until IS NOT NULL))",
            name="valid_calendar_membership",
        ),
        ExcludeConstraint(
            (func.tstzrange(starts_at, occupied_until, "[)"), "&&"),
            where=text("deleted_at IS NULL AND status IN ('pending_channel','confirmed','completed','no_show','blocked')"),
            using="gist",
            name="ex_calendar_no_overlap",
        ),
    )


class CalendarEvent(Base):
    __tablename__ = "calendar_events"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    entry_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), ForeignKey("calendar_entries.id", ondelete="RESTRICT"))
    actor: Mapped[str] = mapped_column(String(80))
    action: Mapped[str] = mapped_column(String(80))
    before: Mapped[dict | None] = mapped_column(JSONB)
    after: Mapped[dict | None] = mapped_column(JSONB)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class ConfirmationToken(Base):
    __tablename__ = "confirmation_tokens"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    entry_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), ForeignKey("calendar_entries.id", ondelete="CASCADE"))
    channel: Mapped[str] = mapped_column(String(16))
    token_sha256: Mapped[str] = mapped_column(String(64), unique=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    used_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    __table_args__ = (CheckConstraint("channel IN ('telegram', 'vk')", name="valid_token_channel"),)


class PublicRateEvent(Base):
    __tablename__ = "public_rate_events"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    identity_hash: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    action: Mapped[str] = mapped_column(String(32), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
