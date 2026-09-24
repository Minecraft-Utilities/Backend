"use client";

import { cn } from "@/common/utils";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import Card, { CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
  Field,
  FieldDescription,
  FieldError,
  FieldGroup,
  FieldLabel,
  FieldSeparator,
  FieldTitle,
} from "@/components/ui/field";
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from "@/components/ui/input-group";
import { Popover, PopoverAnchor, PopoverContent } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import {
  ArrowDown,
  ArrowUp,
  Boxes,
  Check,
  ChevronDown,
  Loader2,
  MapPin,
  Network,
  Signal,
  X,
  type LucideIcon,
} from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useId, useRef, useState, useTransition, type KeyboardEvent } from "react";
import type {
  QueryFilterDefinition,
  QueryFilterFieldDefinition,
  QueryFilterIconName,
  QueryFilterOption,
} from "./types";

export interface QueryFilterBuilderProps {
  definition: QueryFilterDefinition;
  className?: string;
}

/** Query state the builder stages before the user applies it. */
interface FieldState {
  /** Query-parameter values, keyed by parameter name. */
  values: Record<string, string>;
  /** Input text for fields whose visible text differs from their query value. */
  labels: Record<string, string>;
  sortField: string;
  sortDirection: string;
}

const ICONS: Record<QueryFilterIconName, LucideIcon> = {
  network: Network,
  "map-pin": MapPin,
  boxes: Boxes,
  signal: Signal,
};

const DIRECTION_ICONS: Record<string, LucideIcon> = {
  desc: ArrowDown,
  asc: ArrowUp,
};

/**
 * Column classes for a filter grid. Sizing the grid to the field count keeps a
 * short row of filters from leaving a dead column at the end of the panel.
 */
function gridColumns(count: number): string {
  if (count <= 1) {
    return "grid-cols-1";
  }
  if (count === 2) {
    return "grid-cols-1 sm:grid-cols-2";
  }
  if (count === 3) {
    return "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3";
  }
  return "grid-cols-1 sm:grid-cols-2 lg:grid-cols-4";
}

function defaultValueOf(field: QueryFilterFieldDefinition): string {
  return field.defaultValue ?? "";
}

function readFieldState(definition: QueryFilterDefinition, searchParams: URLSearchParams): FieldState {
  const values: Record<string, string> = {};
  const labels: Record<string, string> = {};
  for (const field of definition.fields) {
    values[field.name] = searchParams.get(field.name) ?? defaultValueOf(field);
    if (field.type === "combobox") {
      labels[field.name] = displayValueFor(field, values[field.name] ?? "");
    }
  }
  const sort = definition.sort;
  return {
    values,
    labels,
    sortField: sort ? (searchParams.get(sort.name) ?? sort.defaultValue) : "",
    sortDirection: sort ? (searchParams.get(sort.directionName) ?? sort.defaultDirection) : "",
  };
}

/** Stable fingerprint of the staged state, used to tell "edited" from "already applied". */
function canonicalState(definition: QueryFilterDefinition, state: FieldState): string {
  const entries = definition.fields.map(field => `${field.name}=${(state.values[field.name] ?? "").trim()}`);
  const sort = definition.sort;
  if (sort) {
    entries.push(`${sort.name}=${state.sortField}`, `${sort.directionName}=${state.sortDirection}`);
  }
  return entries.join("&");
}

function isFieldActive(field: QueryFilterFieldDefinition, state: FieldState): boolean {
  const value = (state.values[field.name] ?? "").trim();
  return value.length > 0 && value !== defaultValueOf(field);
}

/** The label a chosen option shows for its value, falling back to the raw value. */
function displayValueFor(field: QueryFilterFieldDefinition, value: string): string {
  const option = field.options?.find(candidate => candidate.value === value);
  if (!option) {
    return value;
  }
  return option.glyph ? `${option.glyph} ${option.label}` : option.label;
}

/**
 * Maps typed suggestion text back to the query value it stands for, so a user
 * can type "United States" or "1.21.4" and still send the code the API expects.
 */
function resolveValue(field: QueryFilterFieldDefinition, text: string): string {
  const option = field.options?.find(
    candidate =>
      candidate.value === text || candidate.label === text || displayValueFor(field, candidate.value) === text
  );
  return option?.value ?? text;
}

/** Message describing why a numeric value is unusable, or `null` when it is fine. */
function wholeNumberIssue(value: string, min: number | undefined, message: string): string | null {
  const trimmed = value.trim();
  if (trimmed.length === 0) {
    return null;
  }
  const parsed = Number(trimmed);
  if (!Number.isInteger(parsed) || (min !== undefined && parsed < min)) {
    return message;
  }
  return null;
}

interface ComboboxFieldProps {
  id: string;
  field: QueryFilterFieldDefinition;
  /** Query value the field currently holds. */
  value: string;
  /** Text the input displays, which differs from `value` when a suggestion is chosen. */
  text: string;
  disabled?: boolean;
  invalid?: boolean;
  onChange: (text: string, value: string) => void;
}

/**
 * Free-text input paired with a filtered suggestion list. Suggestions are a
 * convenience only: whatever is typed is kept as the value, which matters
 * because the tracker matches these filters as substrings.
 */
function ComboboxField({ id, field, value, text, disabled, invalid, onChange }: ComboboxFieldProps) {
  const options = field.options ?? [];
  const Icon = field.icon ? ICONS[field.icon] : undefined;
  const listId = `${id}-suggestions`;
  const anchorRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const [openState, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(-1);

  const open = openState && options.length > 0;
  // A chosen suggestion renders as its label, which is not a substring of itself;
  // treating that text as an empty query keeps the full list reachable.
  const selectedText = displayValueFor(field, value);
  const query = (text === selectedText ? "" : text).trim().toLowerCase();
  const matches =
    query.length === 0
      ? options
      : options.filter(option =>
          [option.value, option.label, option.keywords ?? ""].some(candidate =>
            candidate.toLowerCase().includes(query)
          )
        );
  const activeIndex = matches.length === 0 || highlight < 0 ? -1 : Math.min(highlight, matches.length - 1);

  useEffect(() => {
    if (!open || activeIndex < 0) {
      return;
    }
    document.getElementById(`${id}-option-${activeIndex}`)?.scrollIntoView({ block: "nearest" });
  }, [open, activeIndex, id]);

  const choose = (option: QueryFilterOption) => {
    setOpen(false);
    onChange(displayValueFor(field, option.value), option.value);
    inputRef.current?.focus();
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const descending = event.key === "ArrowDown";
      setOpen(true);
      setHighlight(current => {
        if (matches.length === 0) {
          return -1;
        }
        if (current < 0) {
          return descending ? 0 : matches.length - 1;
        }
        const index = Math.min(current, matches.length - 1);
        return (index + (descending ? 1 : -1) + matches.length) % matches.length;
      });
      return;
    }
    if (event.key === "Enter" && open) {
      // Enter picks the highlighted suggestion, or just closes the list so the
      // next Enter submits the panel.
      event.preventDefault();
      if (activeIndex >= 0) {
        choose(matches[activeIndex]);
      } else {
        setOpen(false);
      }
      return;
    }
    if (event.key === "Escape" && open) {
      event.preventDefault();
      setOpen(false);
    }
  };

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverAnchor asChild>
        <div ref={anchorRef}>
          <InputGroup>
            {Icon ? (
              <InputGroupAddon>
                <Icon className="text-muted-foreground size-4" aria-hidden />
              </InputGroupAddon>
            ) : null}
            <InputGroupInput
              ref={inputRef}
              id={id}
              role="combobox"
              aria-expanded={open}
              aria-controls={open ? listId : undefined}
              aria-autocomplete="list"
              aria-activedescendant={open && activeIndex >= 0 ? `${id}-option-${activeIndex}` : undefined}
              aria-invalid={invalid ? true : undefined}
              autoComplete="off"
              spellCheck={false}
              placeholder={field.placeholder}
              value={text}
              disabled={disabled}
              onFocus={() => {
                setOpen(true);
                setHighlight(matches.findIndex(option => option.value === value));
              }}
              onChange={event => {
                const next = event.target.value;
                setHighlight(0);
                setOpen(true);
                onChange(next, resolveValue(field, next));
              }}
              onKeyDown={handleKeyDown}
            />
            <InputGroupAddon align="inline-end">
              <InputGroupButton
                type="button"
                size="icon-sm"
                variant="ghost"
                aria-label={`Clear ${field.label}`}
                className={cn(text.length === 0 && "pointer-events-none invisible")}
                onClick={() => {
                  setOpen(false);
                  onChange("", "");
                }}
              >
                <X className="size-4" />
              </InputGroupButton>
            </InputGroupAddon>
          </InputGroup>
        </div>
      </PopoverAnchor>
      <PopoverContent
        id={listId}
        role="listbox"
        aria-label={field.label}
        align="start"
        sideOffset={4}
        className="max-h-80 w-(--radix-popover-trigger-width) min-w-(--radix-popover-trigger-width) overflow-x-hidden overflow-y-auto p-1"
        onOpenAutoFocus={event => event.preventDefault()}
        onInteractOutside={event => {
          if (anchorRef.current?.contains(event.target as Node)) {
            event.preventDefault();
          }
        }}
      >
        {matches.length === 0 ? (
          <p className="text-muted-foreground px-2 py-3 text-center text-sm">
            {field.emptyMessage ?? "No suggestions. Your text is used as typed."}
          </p>
        ) : (
          matches.map((option, index) => (
            <div
              key={option.value}
              id={`${id}-option-${index}`}
              role="option"
              aria-selected={option.value === value}
              onPointerDown={event => event.preventDefault()}
              onMouseEnter={() => setHighlight(index)}
              onClick={() => choose(option)}
              className={cn(
                "relative flex w-full cursor-default items-center gap-2 rounded-sm py-1.5 pr-8 pl-2 text-sm outline-hidden select-none",
                index === activeIndex ? "bg-accent text-accent-foreground" : "text-foreground"
              )}
            >
              {option.glyph ? (
                <span className="shrink-0 text-base leading-none" aria-hidden>
                  {option.glyph}
                </span>
              ) : null}
              <span className="min-w-0 flex-1 truncate">{option.label}</span>
              {option.detail ? (
                <span className="text-muted-foreground shrink-0 text-xs tabular-nums">{option.detail}</span>
              ) : null}
              <span className="absolute right-2 flex size-3.5 items-center justify-center">
                {option.value === value ? <Check className="size-4" aria-hidden /> : null}
              </span>
            </div>
          ))
        )}
      </PopoverContent>
    </Popover>
  );
}

export default function QueryFilterBuilder({ definition, className }: QueryFilterBuilderProps) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const advancedPanelId = useId();
  const [isPending, startTransition] = useTransition();
  const [state, setState] = useState<FieldState>(() => readFieldState(definition, searchParams));

  const primaryFields = definition.fields.filter(field => field.group === "primary");
  const advancedFields = definition.fields.filter(field => field.group === "advanced");
  const sort = definition.sort;

  const [advancedOpen, setAdvancedOpen] = useState(() => {
    const initial = readFieldState(definition, searchParams);
    return advancedFields.some(field => isFieldActive(field, initial));
  });

  const appliedState = readFieldState(definition, searchParams);
  const sortActive = sort
    ? state.sortField !== sort.defaultValue || state.sortDirection !== sort.defaultDirection
    : false;

  const chips: Array<{ field: QueryFilterFieldDefinition; text: string }> = [];
  const fieldIssues: Record<string, string | undefined> = {};
  for (const field of definition.fields) {
    const value = (state.values[field.name] ?? "").trim();
    if (field.wholeNumber) {
      const issue = wholeNumberIssue(value, field.wholeNumber.min, field.wholeNumber.message);
      if (issue) {
        fieldIssues[field.name] = issue;
      }
    }
    if (field.notBelow) {
      const floor = Number(state.values[field.notBelow.name] ?? "");
      if (value.length > 0 && Number.isInteger(floor) && Number(value) < floor) {
        fieldIssues[field.name] = field.notBelow.message;
      }
    }
    if (value.length === 0 || value === defaultValueOf(field)) {
      continue;
    }
    chips.push({ field, text: displayValueFor(field, value) });
  }

  const advancedActiveCount = advancedFields.filter(field => isFieldActive(field, state)).length;
  const hasActive = chips.length > 0 || sortActive;
  const hasBlockingIssue = Object.keys(fieldIssues).length > 0;
  const isDirty = canonicalState(definition, state) !== canonicalState(definition, appliedState);

  const setValue = (name: string, value: string) => {
    setState(prev => ({ ...prev, values: { ...prev.values, [name]: value } }));
  };

  const setComboboxText = (name: string, text: string, value: string) => {
    setState(prev => ({
      ...prev,
      values: { ...prev.values, [name]: value },
      labels: { ...prev.labels, [name]: text },
    }));
  };

  const buildNextParams = (next: FieldState): URLSearchParams => {
    const params = new URLSearchParams(searchParams);
    for (const field of definition.fields) {
      params.delete(field.name);
      const value = (next.values[field.name] ?? "").trim();
      if (value.length > 0 && value !== defaultValueOf(field)) {
        params.set(field.name, value);
      }
    }
    if (sort) {
      params.delete(sort.name);
      params.delete(sort.directionName);
      if (next.sortField !== sort.defaultValue) {
        params.set(sort.name, next.sortField);
      }
      if (next.sortDirection !== sort.defaultDirection) {
        params.set(sort.directionName, next.sortDirection);
      }
    }
    params.set("page", "1");
    return params;
  };

  const navigate = (next: FieldState) => {
    const query = buildNextParams(next).toString();
    startTransition(() => {
      router.replace(query.length > 0 ? `${pathname}?${query}` : pathname, { scroll: false });
    });
  };

  const clearAll = () => {
    const next = readFieldState(definition, new URLSearchParams());
    setState(next);
    navigate(next);
  };

  const clearField = (field: QueryFilterFieldDefinition) => {
    setState(prev => {
      const values = { ...prev.values, [field.name]: defaultValueOf(field) };
      const labels = { ...prev.labels };
      if (field.type === "combobox") {
        labels[field.name] = "";
      }
      return { ...prev, values, labels };
    });
  };

  const renderControl = (
    field: QueryFilterFieldDefinition,
    id: string,
    labelId: string,
    invalid: boolean
  ) => {
    const disabled = isPending;
    const value = state.values[field.name] ?? "";
    const Icon = field.icon ? ICONS[field.icon] : undefined;

    if (field.type === "toggle") {
      return (
        <ToggleGroup
          type="single"
          variant="outline"
          value={value}
          aria-labelledby={labelId}
          disabled={disabled}
          className="w-full"
          onValueChange={next => setValue(field.name, next)}
        >
          {(field.options ?? []).map(option => (
            <ToggleGroupItem key={option.value} value={option.value} className="grow basis-0">
              {option.label}
            </ToggleGroupItem>
          ))}
        </ToggleGroup>
      );
    }

    if (field.type === "combobox") {
      return (
        <ComboboxField
          id={id}
          field={field}
          value={value}
          text={state.labels[field.name] ?? value}
          disabled={disabled}
          invalid={invalid}
          onChange={(text, next) => setComboboxText(field.name, text, next)}
        />
      );
    }

    if (field.type === "select") {
      return (
        <Select value={value} onValueChange={next => setValue(field.name, next)} disabled={disabled}>
          <SelectTrigger id={id} className="w-full" aria-invalid={invalid ? true : undefined}>
            {Icon ? <Icon className="size-4 shrink-0" aria-hidden /> : null}
            <SelectValue placeholder={field.placeholder ?? "Select…"} />
          </SelectTrigger>
          <SelectContent>
            {(field.options ?? []).map(option => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }

    return (
      <InputGroup>
        {Icon ? (
          <InputGroupAddon>
            <Icon className="text-muted-foreground size-4" aria-hidden />
          </InputGroupAddon>
        ) : null}
        <InputGroupInput
          id={id}
          type={field.type === "number" ? "number" : "text"}
          inputMode={field.type === "number" ? (field.inputMode ?? "numeric") : undefined}
          min={field.min}
          max={field.max}
          step={field.step}
          placeholder={field.placeholder}
          aria-invalid={invalid ? true : undefined}
          value={value}
          disabled={disabled}
          onChange={event => setValue(field.name, event.target.value)}
        />
      </InputGroup>
    );
  };

  const renderField = (field: QueryFilterFieldDefinition) => {
    const issue = fieldIssues[field.name];
    const invalid = issue !== undefined;
    const controlId = `filter-${field.name}`;
    const labelId = `${controlId}-label`;
    return (
      <Field key={field.name} data-invalid={invalid || undefined} className="min-w-0">
        {field.type === "toggle" ? (
          <FieldTitle id={labelId}>{field.label}</FieldTitle>
        ) : (
          <FieldLabel htmlFor={controlId}>{field.label}</FieldLabel>
        )}
        {renderControl(field, controlId, labelId, invalid)}
        {field.description ? <FieldDescription>{field.description}</FieldDescription> : null}
        <FieldError>{issue}</FieldError>
      </Field>
    );
  };

  return (
    <form
      className={cn("w-full", className)}
      onSubmit={event => {
        event.preventDefault();
        if (!isDirty || hasBlockingIssue) {
          return;
        }
        navigate(state);
      }}
    >
      <Card className="w-full">
        <CardHeader className="px-4 py-3 text-sm font-normal tracking-normal normal-case">
          <h2 className="text-foreground text-sm font-semibold">{definition.title ?? "Filter"}</h2>
          {definition.description ? (
            <p className="text-muted-foreground mt-0.5 text-xs leading-snug text-pretty">
              {definition.description}
            </p>
          ) : null}
        </CardHeader>

        <CardContent className="flex flex-col gap-4 p-4">
          {chips.length > 0 ? (
            <div className="flex flex-wrap items-center gap-1.5">
              {chips.map(chip => (
                <Badge key={chip.field.name} variant="secondary" className="max-w-full gap-1 pr-1">
                  <span className="text-muted-foreground shrink-0">{chip.field.label}</span>
                  <span className="min-w-0 truncate">{chip.text}</span>
                  <button
                    type="button"
                    aria-label={`Remove ${chip.field.label} filter`}
                    onClick={() => clearField(chip.field)}
                    className="hover:text-foreground focus-visible:ring-ring/50 -mr-0.5 rounded-full p-0.5 transition-colors focus-visible:ring-2 focus-visible:outline-none"
                  >
                    <X aria-hidden />
                  </button>
                </Badge>
              ))}
            </div>
          ) : null}

          <FieldGroup>
            <div className={cn("grid gap-4", gridColumns(primaryFields.length))}>
              {primaryFields.map(renderField)}
            </div>

            {advancedFields.length > 0 ? (
              <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen}>
                <CollapsibleTrigger asChild>
                  <Button type="button" variant="ghost" size="sm" className="text-muted-foreground">
                    <ChevronDown
                      className={cn("transition-transform duration-200", advancedOpen && "rotate-180")}
                      aria-hidden
                    />
                    More filters
                    {advancedActiveCount > 0 ? (
                      <Badge variant="secondary" className="tabular-nums">
                        {advancedActiveCount}
                      </Badge>
                    ) : null}
                  </Button>
                </CollapsibleTrigger>
                <CollapsibleContent id={advancedPanelId}>
                  <div className={cn("grid gap-4 pt-4", gridColumns(advancedFields.length))}>
                    {advancedFields.map(renderField)}
                  </div>
                </CollapsibleContent>
              </Collapsible>
            ) : null}

            {sort ? (
              <>
                <FieldSeparator />
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field className="min-w-0">
                    <FieldLabel htmlFor={`filter-${sort.name}`}>{sort.label}</FieldLabel>
                    <Select
                      value={state.sortField}
                      onValueChange={next => setState(prev => ({ ...prev, sortField: next }))}
                      disabled={isPending}
                    >
                      <SelectTrigger id={`filter-${sort.name}`} className="w-full">
                        <SelectValue placeholder={sort.defaultValue} />
                      </SelectTrigger>
                      <SelectContent>
                        {sort.options.map(option => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </Field>
                  <Field className="min-w-0">
                    <FieldTitle id={`filter-${sort.directionName}-label`}>{sort.directionLabel}</FieldTitle>
                    <ToggleGroup
                      type="single"
                      variant="outline"
                      value={state.sortDirection}
                      aria-labelledby={`filter-${sort.directionName}-label`}
                      disabled={isPending}
                      onValueChange={next => setState(prev => ({ ...prev, sortDirection: next }))}
                    >
                      {sort.directionOptions.map(option => {
                        const DirectionIcon = DIRECTION_ICONS[option.value];
                        return (
                          <ToggleGroupItem key={option.value} value={option.value} aria-label={option.label}>
                            {DirectionIcon ? <DirectionIcon aria-hidden /> : option.label}
                          </ToggleGroupItem>
                        );
                      })}
                    </ToggleGroup>
                  </Field>
                </div>
              </>
            ) : null}
          </FieldGroup>
        </CardContent>

        <CardFooter className="flex flex-wrap items-center justify-between gap-3 px-4 py-3">
          <p className="text-muted-foreground text-xs" aria-live="polite">
            {hasBlockingIssue ? (
              <span className="text-destructive font-medium">Fix the highlighted fields to continue.</span>
            ) : isDirty ? (
              "Unsaved changes"
            ) : null}
          </p>
          <Field orientation="horizontal" className="ml-auto w-auto">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={clearAll}
              disabled={isPending || !hasActive}
            >
              Clear all
            </Button>
            <Button type="submit" size="sm" disabled={isPending || !isDirty || hasBlockingIssue}>
              {isPending ? <Loader2 className="animate-spin" aria-hidden /> : <Check aria-hidden />}
              Apply filters
            </Button>
          </Field>
        </CardFooter>
      </Card>
    </form>
  );
}
