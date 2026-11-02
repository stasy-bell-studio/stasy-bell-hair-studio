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
    consent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
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


class FinanceTransaction(Base):
    __tablename__ = "finance_transactions"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    entry_id: Mapped[UUID | None] = mapped_column(
        PGUUID(as_uuid=True), ForeignKey("calendar_entries.id", ondelete="RESTRICT")
    )
    kind: Mapped[str] = mapped_column(String(10), nullable=False)
    category: Mapped[str] = mapped_column(String(40), nullable=False)
    amount_kopecks: Mapped[int] = mapped_column(Integer, nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    note: Mapped[str | None] = mapped_column(Text)
    correction_of_id: Mapped[UUID | None] = mapped_column(
        PGUUID(as_uuid=True), ForeignKey("finance_transactions.id", ondelete="RESTRICT"),
        unique=True,
    )
    created_by: Mapped[str] = mapped_column(String(80), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (
        CheckConstraint("kind IN ('income','expense')", name="valid_finance_kind"),
        CheckConstraint("amount_kopecks > 0", name="positive_finance_amount"),
    )


class MonthClosure(Base):
    __tablename__ = "month_closures"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    year: Mapped[int] = mapped_column(Integer, nullable=False)
    month: Mapped[int] = mapped_column(Integer, nullable=False)
    version: Mapped[int] = mapped_column(Integer, nullable=False)
    income_kopecks: Mapped[int] = mapped_column(Integer, nullable=False)
    expense_kopecks: Mapped[int] = mapped_column(Integer, nullable=False)
    balance_kopecks: Mapped[int] = mapped_column(Integer, nullable=False)
    closed_by: Mapped[str] = mapped_column(String(80), nullable=False)
    closed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    reopened_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    reopened_by: Mapped[str | None] = mapped_column(String(80))
    reopen_reason: Mapped[str | None] = mapped_column(Text)
    __table_args__ = (
        UniqueConstraint("year", "month", "version", name="uq_month_closure_version"),
        CheckConstraint("month BETWEEN 1 AND 12 AND version > 0", name="valid_closure_period"),
    )


class Material(Base):
    __tablename__ = "materials"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(200), nullable=False, unique=True)
    unit: Mapped[str] = mapped_column(String(8), nullable=False)
    stock_milliunits: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    __table_args__ = (
        CheckConstraint("unit IN ('g','ml','pcs')", name="valid_material_unit"),
        CheckConstraint("stock_milliunits >= 0", name="nonnegative_material_stock"),
    )


class MaterialMovement(Base):
    __tablename__ = "material_movements"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    material_id: Mapped[UUID] = mapped_column(
        PGUUID(as_uuid=True), ForeignKey("materials.id", ondelete="RESTRICT"),
        nullable=False,
    )
    change_milliunits: Mapped[int] = mapped_column(Integer, nullable=False)
    reason: Mapped[str] = mapped_column(String(200), nullable=False)
    created_by: Mapped[str] = mapped_column(String(80), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (
        CheckConstraint("change_milliunits <> 0", name="nonzero_material_movement"),
    )


class OwnerNotification(Base):
    __tablename__ = "owner_notifications"
    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    entry_id: Mapped[UUID] = mapped_column(
        PGUUID(as_uuid=True), ForeignKey("calendar_entries.id", ondelete="RESTRICT"), nullable=False,
    )
    event_type: Mapped[str] = mapped_column(String(32), nullable=False)
    state: Mapped[str] = mapped_column(String(16), nullable=False, server_default=text("'pending'"))
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("0"))
    next_attempt_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), nullable=False,
    )
    sent_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_error_code: Mapped[str | None] = mapped_column(String(80))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    __table_args__ = (
        UniqueConstraint("entry_id", "event_type", name="uq_owner_notification_once"),
        CheckConstraint("state IN ('pending','sent','stopped')", name="valid_owner_notification_state"),
        CheckConstraint("attempts >= 0", name="nonnegative_delivery_attempts"),
    )
