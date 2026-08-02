package com.labelmate.labelmate.model;

/**
 * User role. DATASET_OWNER is not a separate role value — it is derived from
 * ownership (datasets.owner_id / projects.owner_id). ANNOTATOR covers both
 * Dataset Owner and Annotator capabilities per database-design.md.
 */
public enum Role {
    ADMIN,
    ANNOTATOR
}
