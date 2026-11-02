"""Append-only actual cash, versioned month closures, minimal stock ledger.

Revision ID: 0005_finance
Revises: 0004_messenger_hold
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0005_finance"
down_revision = "0004_messenger_hold"
branch_labels = None
depends_on = None

U = postgresql.UUID(as_uuid=True)
DT = sa.DateTime(timezone=True)


def upgrade():
    op.create_table(
        "finance_transactions",
        sa.Column("id", U, primary_key=True),
        sa.Column("entry_id", U, sa.ForeignKey("calendar_entries.id", ondelete="RESTRICT")),
        sa.Column("kind", sa.String(10), nullable=False),
        sa.Column("category", sa.String(40), nullable=False),
        sa.Column("amount_kopecks", sa.Integer(), nullable=False),
        sa.Column("occurred_at", DT, nullable=False),
        sa.Column("note", sa.Text()),
        sa.Column("correction_of_id", U,
                  sa.ForeignKey("finance_transactions.id", ondelete="RESTRICT"), unique=True),
        sa.Column("created_by", sa.String(80), nullable=False),
        sa.Column("created_at", DT, nullable=False, server_default=sa.func.now()),
        sa.CheckConstraint("kind IN ('income','expense')", name="valid_finance_kind"),
        sa.CheckConstraint("amount_kopecks > 0", name="positive_finance_amount"),
    )
    op.create_table(
        "month_closures",
        sa.Column("id", U, primary_key=True),
        sa.Column("year", sa.Integer(), nullable=False),
        sa.Column("month", sa.Integer(), nullable=False),
        sa.Column("version", sa.Integer(), nullable=False),
        sa.Column("income_kopecks", sa.Integer(), nullable=False),
        sa.Column("expense_kopecks", sa.Integer(), nullable=False),
        sa.Column("balance_kopecks", sa.Integer(), nullable=False),
        sa.Column("closed_by", sa.String(80), nullable=False),
        sa.Column("closed_at", DT, nullable=False, server_default=sa.func.now()),
        sa.Column("reopened_at", DT),
        sa.Column("reopened_by", sa.String(80)),
        sa.Column("reopen_reason", sa.Text()),
        sa.UniqueConstraint("year", "month", "version", name="uq_month_closure_version"),
        sa.CheckConstraint("month BETWEEN 1 AND 12 AND version > 0", name="valid_closure_period"),
    )
    op.create_table(
        "materials",
        sa.Column("id", U, primary_key=True),
        sa.Column("name", sa.String(200), nullable=False, unique=True),
        sa.Column("unit", sa.String(8), nullable=False),
        sa.Column("stock_milliunits", sa.Integer(), nullable=False, server_default="0"),
        sa.CheckConstraint("unit IN ('g','ml','pcs')", name="valid_material_unit"),
        sa.CheckConstraint("stock_milliunits >= 0", name="nonnegative_material_stock"),
    )
    op.create_table(
        "material_movements",
        sa.Column("id", U, primary_key=True),
        sa.Column("material_id", U, sa.ForeignKey("materials.id", ondelete="RESTRICT"),
                  nullable=False),
        sa.Column("change_milliunits", sa.Integer(), nullable=False),
        sa.Column("reason", sa.String(200), nullable=False),
        sa.Column("created_by", sa.String(80), nullable=False),
        sa.Column("created_at", DT, nullable=False, server_default=sa.func.now()),
        sa.CheckConstraint("change_milliunits <> 0", name="nonzero_material_movement"),
    )


def downgrade():
    op.drop_table("material_movements")
    op.drop_table("materials")
    op.drop_table("month_closures")
    op.drop_table("finance_transactions")
