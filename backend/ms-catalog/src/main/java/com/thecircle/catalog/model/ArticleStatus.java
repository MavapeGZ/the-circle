package com.thecircle.catalog.model;

/**
 * Availability of an article in the catalog.
 * AVAILABLE: open for new contracts.
 * RESERVED: a contract exists but is not yet signed by both parties.
 * SOLD: contract signed by both parties; hidden from catalog searches.
 * DELETED: soft-deleted by the author; hidden from catalog searches but still
 * present in the index for historical reference.
 */
public enum ArticleStatus {
    AVAILABLE,
    RESERVED,
    SOLD,
    DELETED
}
