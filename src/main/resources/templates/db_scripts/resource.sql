CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.extract_text_elements(input jsonb) RETURNS SETOF text AS $$
DECLARE
        key text;
        value jsonb;
BEGIN
        CASE jsonb_typeof(input)
                WHEN 'object' THEN
                        FOR key, value IN SELECT * FROM jsonb_each(input) LOOP
                                RETURN QUERY SELECT extract_text_elements(value);
                        END LOOP;
                WHEN 'array' THEN
                        FOR value IN SELECT jsonb_array_elements(input) LOOP
                                RETURN QUERY SELECT extract_text_elements(value);
                        END LOOP;
                WHEN 'string' THEN
                        RETURN NEXT input #>> '{}';
                ELSE
                        NULL;
        END CASE;
END
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.extract_keyword_element(input jsonb) RETURNS SETOF text AS $$
DECLARE
        key text;
        value jsonb;
BEGIN
        CASE jsonb_typeof(input)
                WHEN 'object' THEN
                        FOR key, value IN SELECT * FROM jsonb_each(input) LOOP
                          case key
                            when 'title' then
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            when 'term' then
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            when 'terms' then
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            when 'subject' then
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            when 'description' then
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            else
                              null;
                          end case;
                        END LOOP;
                WHEN 'array' THEN
                        FOR value IN SELECT jsonb_array_elements(input) LOOP
                                RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                        END LOOP;
                WHEN 'string' THEN
                        RETURN NEXT input #>> '{}';
                ELSE
                        NULL;
        END CASE;
END
$$ LANGUAGE plpgsql;

-- alter function ${myuniversity}_${mymodule}.extract_keyword_element(input jsonb) owner to folio;

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.combine_keyword_element(input jsonb) RETURNS text AS $$
DECLARE
  keywords text;
BEGIN
  select string_agg(txt, ' ') into keywords from ${myuniversity}_${mymodule}.extract_text_elements(input) as txt;
  return keywords;
END;
$$ LANGUAGE plpgsql IMMUTABLE ;

-- alter function ${myuniversity}_${mymodule}.combine_keyword_element(input jsonb) owner to folio;

-- ALTER TABLE ${myuniversity}_${mymodule}.resource ADD column keywords tsvector;
-- create index idx_keyword_full_text on ${myuniversity}_${mymodule}.resource using gin(keywords);

create function ${myuniversity}_${mymodule}.resource_set_keywords()
  returns trigger
as $$
DECLARE
  keywords text;
BEGIN
--   NEW.keywords = to_tsvector('english', ${myuniversity}_${mymodule}.combine_keyword_element(NEW.jsonb));
  keywords = replace(${myuniversity}_${mymodule}.combine_keyword_element(NEW.jsonb), '"', ' ');
  NEW.jsonb = jsonb_set(NEW.jsonb, '{keywords}', concat('"', keywords, '"')::jsonb, true);
--   NEW.keywords = to_tsvector('English', NEW.jsonb::text);
  RETURN NEW;
END;
$$ language plpgsql;

-- alter function ${myuniversity}_${mymodule}.resource_set_keywords() owner to folio;

create trigger set_resource_keywords_trigger
  before insert or update
  on resource
  for each row
execute procedure resource_set_keywords();

-- Create a view of all tags
CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.tag_view AS
SELECT distinct jsonb_array_elements_text(resource.jsonb->'tags'->'tagList') tag
from ${myuniversity}_${mymodule}.resource;
GRANT SELECT ON ${myuniversity}_${mymodule}.tag_view TO ${myuniversity}_${mymodule};


