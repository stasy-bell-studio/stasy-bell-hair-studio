"""Private historical spreadsheet archive, isolated from live calendar and finance.

Revision ID: 0008_private_legacy_archive
Revises: 0007_owner_sessions
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

revision = "0008_private_legacy_archive"
down_revision = "0007_owner_sessions"
branch_labels = None
depends_on = None


def upgrade():
    op.create_table(
        "legacy_client_refs",
        sa.Column("ref", sa.String(200), primary_key=True),
        sa.Column("client_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("clients.id", ondelete="RESTRICT"), nullable=False),
    )
    op.create_index("ix_legacy_client_refs_client_id", "legacy_client_refs", ["client_id"])
    op.create_table(
        "legacy_visits",
        sa.Column("ref", sa.String(200), primary_key=True),
        sa.Column("client_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("clients.id", ondelete="RESTRICT")),
        sa.Column("visited_on", sa.Date(), nullable=False),
        sa.Column("service_label", sa.String(300), nullable=False),
        sa.Column("amount_kopecks", sa.Integer()),
        sa.Column("amount_origin", sa.String(50), nullable=False),
        sa.Column("consumables_kopecks", sa.Integer()),
        sa.Column("notes", sa.Text()),
        sa.Column("source_sheet", sa.String(120), nullable=False),
        sa.Column("source_row", sa.Integer()),
        sa.CheckConstraint("amount_kopecks IS NULL OR amount_kopecks >= 0",
                           name="nonnegative_legacy_amount"),
        sa.CheckConstraint("consumables_kopecks IS NULL OR consumables_kopecks >= 0",
                           name="nonnegative_legacy_cost"),
    )
    op.create_index("ix_legacy_visits_client_id", "legacy_visits", ["client_id"])
    op.create_index("ix_legacy_visits_visited_on", "legacy_visits", ["visited_on"])
    op.create_table(
        "legacy_catalog_items",
        sa.Column("ref", sa.String(200), primary_key=True),
        sa.Column("name", sa.String(200), nullable=False),
        sa.Column("category", sa.String(200)),
        sa.Column("price_kopecks", sa.Integer()),
        sa.Column("cost_estimate_kopecks", sa.Integer()),
        sa.Column("active_in_source", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.create_table(
        "legacy_monthly_summaries",
        sa.Column("period", sa.String(7), primary_key=True),
        sa.Column("revenue_kopecks", sa.Integer()),
        sa.Column("consumables_kopecks", sa.Integer()),
        sa.Column("rent_kopecks", sa.Integer()),
        sa.Column("utilities_kopecks", sa.Integer()),
        sa.Column("other_expenses_kopecks", sa.Integer()),
        sa.Column("net_profit_kopecks", sa.Integer()),
        sa.Column("unique_clients", sa.Integer()),
        sa.Column("total_visits", sa.Integer()),
    )
    op.create_table(
        "legacy_import_batches",
        sa.Column("dataset_sha256", sa.String(64), primary_key=True),
        sa.Column("counts", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        sa.Column("imported_at", sa.DateTime(timezone=True), nullable=False,
                  server_default=sa.func.now()),
    )


def downgrade():
    op.drop_table("legacy_import_batches")
    op.drop_table("legacy_monthly_summaries")
    op.drop_table("legacy_catalog_items")
    op.drop_index("ix_legacy_visits_visited_on", table_name="legacy_visits")
    op.drop_index("ix_legacy_visits_client_id", table_name="legacy_visits")
    op.drop_table("legacy_visits")
    op.drop_index("ix_legacy_client_refs_client_id", table_name="legacy_client_refs")
    op.drop_table("legacy_client_refs")
