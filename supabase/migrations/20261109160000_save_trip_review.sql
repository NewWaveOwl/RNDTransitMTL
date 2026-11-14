begin;

-- Keep the existing column grants and row policies in force for the caller.
create function public.save_trip_review(p_trip_id uuid, p_review jsonb)
returns table (
    overall integer, quality integer, interesting integer, fun integer,
    comment text, image_url text
)
language sql
security invoker
set search_path = ''
as $$
    insert into public.reviews as review (trip_id, overall, quality, interesting, fun, comment, image_url)
    select p_trip_id, input.overall, input.quality, input.interesting, input.fun, input.comment, input.image_url
    from jsonb_to_record(p_review) as input (
        overall integer, quality integer, interesting integer, fun integer,
        comment text, image_url text
    )
    on conflict on constraint reviews_pkey do update set
        overall = excluded.overall,
        quality = excluded.quality,
        interesting = excluded.interesting,
        fun = excluded.fun,
        comment = excluded.comment,
        image_url = excluded.image_url
    returning review.overall, review.quality, review.interesting, review.fun, review.comment, review.image_url;
$$;

revoke execute on function public.save_trip_review(uuid, jsonb) from public, anon;
grant execute on function public.save_trip_review(uuid, jsonb) to authenticated;

commit;
