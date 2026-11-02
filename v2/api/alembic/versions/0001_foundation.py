"""Initial schema; one timeline for visits and schedule blocks.

Revision ID: 0001_foundation
Revises:
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0001_foundation"
down_revision = None
branch_labels = None
depends_on = None

U = postgresql.UUID(as_uuid=True)
DT = sa.DateTime(timezone=True)


def upgrade() -> None:
    op.create_table(
        "clients",
        sa.Column("id", U, primary_key=True),
        sa.Column("name", sa.String(200), nullable=False),
        sa.Column("phone", sa.String(40)),
        sa.Column("preferred_channel", sa.String(16)),
        sa.Column("telegram_chat_id", sa.String(100)),
        sa.Column("vk_user_id", sa.String(100)),
        sa.Column("notes", sa.Text()),
        sa.Column("created_at", DT, server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint("preferred_channel IS NULL OR preferred_channel IN ('telegram', 'vk')", name="client_channel"),
    )
    op.create_table(
        "services",
        sa.Column("id", U, primary_key=True),
        sa.Column("code", sa.String(120), nullable=False, unique=True),
        sa.Column("name", sa.String(200), nullable=False),
        sa.Column("variant", sa.String(200)),
        sa.Column("price_kopecks", sa.Integer()),
        sa.Column("duration_minutes", sa.Integer()),
        sa.Column("buffer_minutes", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.text("false")),
        sa.CheckConstraint("price_kopecks IS NULL OR price_kopecks >= 0", name="nonnegative_price"),
        sa.CheckConstraint("duration_minutes IS NULL OR duration_minutes > 0", name="positive_duration"),
        sa.CheckConstraint("buffer_minutes >= 0", name="nonnegative_buffer"),
    )
    op.create_table(
        "working_windows",
        sa.Column("id", U, primary_key=True),
        sa.Column("day", sa.Date(), nullable=False),
        sa.Column("starts_at", sa.Time(), nullable=False),
        sa.Column("ends_at", sa.Time(), nullable=False),
        sa.CheckConstraint("ends_at > starts_at", name="valid_working_window"),
        sa.UniqueConstraint("day", "starts_at", "ends_at", name="uq_working_window"),
    )
    op.create_table(
        "calendar_entries",
        sa.Column("id", U, primary_key=True),
        sa.Column("kind", sa.String(16), nullable=False),
        sa.Column("client_id", U, sa.ForeignKey("clients.id", ondelete="RESTRICT")),
        sa.Column("service_id", U, sa.ForeignKey("services.id", ondelete="RESTRICT")),
        sa.Column("source", sa.String(32), nullable=False),
        sa.Column("status", sa.String(24), nullable=False),
        sa.Column("starts_at", DT, nullable=False),
        sa.Column("ends_at", DT, nullable=False),
        sa.Column("occupied_until", DT, nullable=False),
        sa.Column("hold_until", DT),
        sa.Column("deleted_at", DT),
        sa.Column("created_at", DT, server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint("ends_at > starts_at AND occupied_until >= ends_at", name="valid_calendar_times"),
        sa.CheckConstraint("kind IN ('appointment','block')", name="valid_calendar_kind"),
        sa.CheckConstraint("status IN ('pending_channel','confirmed','expired','cancelled','completed','no_show','blocked')", name="valid_calendar_status"),
        sa.CheckConstraint(
            "(kind = 'block' AND status = 'blocked' AND client_id IS NULL AND service_id IS NULL AND hold_until IS NULL) "
            "OR (kind = 'appointment' AND client_id IS NOT NULL AND service_id IS NOT NULL "
            "AND status <> 'blocked' AND (status <> 'pending_channel' OR hold_until IS NOT NULL))",
            name="valid_calendar_membership",
        ),
    )
    # Static partial predicate keeps the exclusion constraint safe and immutable.
    # The worker / transaction must flip elapsed pending holds to expired.
    op.execute(
        "ALTER TABLE calendar_entries ADD CONSTRAINT ex_calendar_no_overlap "
        "EXCLUDE USING gist (tstzrange(starts_at, occupied_until, '[)') WITH &&) "
        "WHERE (deleted_at IS NULL AND status IN "
        "('pending_channel','confirmed','completed','no_show','blocked'))"
    )
    op.create_table(
        "calendar_events",
        sa.Column("id", U, primary_key=True),
        sa.Column("entry_id", U, sa.ForeignKey("calendar_entries.id", ondelete="RESTRICT"), nullable=False),
        sa.Column("actor", sa.String(80), nullable=False),
        sa.Column("action", sa.String(80), nullable=False),
        sa.Column("before", postgresql.JSONB()),
        sa.Column("after", postgresql.JSONB()),
        sa.Column("occurred_at", DT, server_default=sa.func.now(), nullable=False),
    )
    op.create_table(
        "confirmation_tokens",
        sa.Column("id", U, primary_key=True),
        sa.Column("entry_id", U, sa.ForeignKey("calendar_entries.id", ondelete="CASCADE"), nullable=False),
        sa.Column("channel", sa.String(16), nullable=False),
        sa.Column("token_sha256", sa.String(64), nullable=False, unique=True),
        sa.Column("expires_at", DT, nullable=False),
        sa.Column("used_at", DT),
        sa.CheckConstraint("channel IN ('telegram', 'vk')", name="valid_token_channel"),
    )


def downgrade() -> None:
    op.drop_table("confirmation_tokens")
    op.drop_table("calendar_events")
    op.drop_table("calendar_entries")
    op.drop_table("working_windows")
    op.drop_table("services")
    op.drop_table("clients")
