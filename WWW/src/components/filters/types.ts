/**
 * Serializable, page-agnostic definitions that drive the generic
 * {@link QueryFilterBuilder}. Keep these pure data so any list page can
 * wire up the builder by supplying one configuration object.
 */

/** A single selectable option inside a select, toggle group, or combobox field. */
export interface QueryFilterOption {
  /** Value written to the query parameter when this option is chosen. */
  value: string;
  /** Human-readable text shown for this option. */
  label: string;
  /** Trailing text on a combobox suggestion row, eg: a match count. */
  detail?: string;
  /** Leading glyph on a combobox suggestion row, eg: a flag emoji. */
  glyph?: string;
  /**
   * Extra text a combobox matches on beyond `value` and `label`, so an option
   * can be found by a code its label does not contain.
   */
  keywords?: string;
}

/** Leading icon rendered inside a field's control. */
export type QueryFilterIconName = "network" | "map-pin" | "boxes" | "signal";

/** Declarative constraint that keeps free text out of a numeric query parameter. */
export interface QueryFilterWholeNumberConstraint {
  /** Smallest accepted value, inclusive. */
  min?: number;
  /** Message shown under the field when the value is rejected. */
  message: string;
}

/** Declarative cross-field rule between two numeric query parameters. */
export interface QueryFilterLowerBoundConstraint {
  /** Name of the field whose value acts as the floor. */
  name: string;
  /** Message shown under the field when the rule is broken. */
  message: string;
}

/** A single filter field, backed by exactly one query parameter. */
export interface QueryFilterFieldDefinition {
  /** Query-parameter name this field owns, also used for labels, chips, and React keys. */
  name: string;
  /** Human-readable label for the field. */
  label: string;
  /** Which layout group this field belongs to. */
  group: "primary" | "advanced";
  /** Control rendered for this field. */
  type: "text" | "number" | "select" | "toggle" | "combobox";
  /** Placeholder shown when the field is empty. */
  placeholder?: string;
  /** Short helper text shown under the field. */
  description?: string;
  /** Leading icon rendered inside the field's control. */
  icon?: QueryFilterIconName;
  /**
   * The value the field is considered "unset" at. Blank and default values
   * are omitted from the serialized URL.
   */
  defaultValue?: string;
  /** Minimum value for number fields. */
  min?: number;
  /** Maximum value for number fields. */
  max?: number;
  /** Step for number fields. */
  step?: number;
  /** Input mode hint for number fields. */
  inputMode?: "numeric" | "decimal";
  /** Options for `select`, `toggle`, and `combobox` fields. */
  options?: QueryFilterOption[];
  /** Message a combobox shows when nothing matches the typed text. */
  emptyMessage?: string;
  /** Rejects any value the API would not accept as a whole number. */
  wholeNumber?: QueryFilterWholeNumberConstraint;
  /** Rejects any value below another numeric field's value. */
  notBelow?: QueryFilterLowerBoundConstraint;
}

/**
 * The sort control rendered by the builder.
 *
 * `name` is the query-parameter for the sort field, and `directionName` is
 * the query-parameter for the sort direction.
 */
export interface QuerySortDefinition {
  /** Query-parameter name for the sort field. */
  name: string;
  /** Query-parameter name for the sort direction. */
  directionName: string;
  /** Label for the sort field control. */
  label: string;
  /** Label for the direction control. */
  directionLabel: string;
  /** Available sort fields. */
  options: QueryFilterOption[];
  /** Available sort directions, rendered as an icon toggle group. */
  directionOptions: QueryFilterOption[];
  /** Default sort field value (treated as "unset" when equal). */
  defaultValue: string;
  /** Default direction value (treated as "unset" when equal). */
  defaultDirection: string;
}

/** The full, serializable configuration consumed by the builder. */
export interface QueryFilterDefinition {
  fields: QueryFilterFieldDefinition[];
  sort?: QuerySortDefinition;
  /** Optional panel heading; defaults to "Filter". */
  title?: string;
  /** Optional helper text displayed under the heading. */
  description?: string;
}
