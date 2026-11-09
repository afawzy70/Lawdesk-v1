-- LD-057 (WP9, requirements section 28): the excerpt the unified search shows for a result. It is cut from the ORIGINAL text, around
-- the first word that matches a word of the query (matched with library_normalize, so hamza, taa marbuta, diacritics and the
-- article are forgiven exactly as they are in the index), and is called only on a record the signed-in user may see.
-- With no matching word it is the start of the text. p_around words before and p_after words after the match are kept.
create function search_snippet(p_text text, p_query text, p_around integer default 6, p_after integer default 14)
    returns text language sql immutable parallel safe as $$
with words as (
    select w, n, library_normalize(w) as nw
    from regexp_split_to_table(coalesce(p_text, ''), '\s+') with ordinality as t (w, n)
    where w <> ''),
terms as (
    select t from unnest(string_to_array(library_normalize(p_query), ' ')) as t where t <> ''),
hit as (
    select coalesce(min(words.n), 1) as n
    from words
    where exists (select 1 from terms where exists (
        select 1 from unnest(string_to_array(words.nw, ' ')) as piece where piece like terms.t || '%'))),
cut as (
    select greatest(hit.n - p_around, 1) as first, hit.n + p_after as last, hit.n from hit)
select case when count(words.w) = 0 then null else
    (case when cut.first > 1 then '… ' else '' end)
    || string_agg(words.w, ' ' order by words.n)
    || (case when cut.last < (select max(n) from words) then ' …' else '' end) end
from words cross join cut
where words.n between cut.first and cut.last
group by cut.first, cut.last
$$;
