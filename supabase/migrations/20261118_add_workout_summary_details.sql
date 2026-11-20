-- Preserve the existing workout_records table and add per-set detail used by the app's
-- measured set/session summaries. Existing measured rows are classified as unassessed
-- because their historical form/attempt detail was not recorded.

alter table public.workout_records
    add column if not exists good_form_reps_by_set integer[],
    add column if not exists attention_reps_by_set integer[],
    add column if not exists unassessed_reps_by_set integer[],
    add column if not exists incomplete_attempts_by_set integer[],
    add column if not exists tracking_interruptions_by_set integer[];

update public.workout_records
set good_form_reps_by_set = array_fill(0, array[cardinality(per_set_reps)]),
    attention_reps_by_set = array_fill(0, array[cardinality(per_set_reps)]),
    unassessed_reps_by_set = per_set_reps,
    incomplete_attempts_by_set = array_fill(0, array[cardinality(per_set_reps)]),
    tracking_interruptions_by_set = array_fill(0, array[cardinality(per_set_reps)])
where kind = 'MEASURED'
  and per_set_reps is not null
  and good_form_reps_by_set is null
  and attention_reps_by_set is null
  and unassessed_reps_by_set is null
  and incomplete_attempts_by_set is null
  and tracking_interruptions_by_set is null;

alter table public.workout_records
    drop constraint if exists workout_records_summary_details_check;

alter table public.workout_records
    add constraint workout_records_summary_details_check check (
        (kind <> 'MEASURED' and
            good_form_reps_by_set is null and
            attention_reps_by_set is null and
            unassessed_reps_by_set is null and
            incomplete_attempts_by_set is null and
            tracking_interruptions_by_set is null)
        or
        (kind = 'MEASURED' and per_set_reps is not null and
            good_form_reps_by_set is not null and
            attention_reps_by_set is not null and
            unassessed_reps_by_set is not null and
            incomplete_attempts_by_set is not null and
            tracking_interruptions_by_set is not null and
            cardinality(good_form_reps_by_set) = cardinality(per_set_reps) and
            cardinality(attention_reps_by_set) = cardinality(per_set_reps) and
            cardinality(unassessed_reps_by_set) = cardinality(per_set_reps) and
            cardinality(incomplete_attempts_by_set) = cardinality(per_set_reps) and
            cardinality(tracking_interruptions_by_set) = cardinality(per_set_reps))
    );
