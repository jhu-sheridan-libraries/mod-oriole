CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.extract_text_elements(input jsonb) RETURNS SETOF text AS $$
DECLARE
        key text;
        value jsonb;
BEGIN
        CASE jsonb_typeof(input)
                WHEN 'object' THEN
                        FOR key, value IN SELECT * FROM jsonb_each(input) LOOP
                                RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_text_elements(value);
                        END LOOP;
                WHEN 'array' THEN
                        FOR value IN SELECT jsonb_array_elements(input) LOOP
                                RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_text_elements(value);
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
                          CASE key
                            WHEN 'title' THEN
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            WHEN 'term' THEN
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            WHEN 'terms' THEN
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            WHEN 'subject' THEN
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            WHEN 'description' THEN
                              RETURN QUERY SELECT ${myuniversity}_${mymodule}.extract_keyword_element(value);
                            ELSE
                              NULL;
                          END CASE;
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
  SELECT string_agg(txt, ' ') INTO keywords FROM ${myuniversity}_${mymodule}.extract_text_elements(input) AS txt;
  return keywords;
END;
$$ LANGUAGE plpgsql IMMUTABLE ;

-- alter function ${myuniversity}_${mymodule}.combine_keyword_element(input jsonb) owner to folio;

-- ALTER TABLE ${myuniversity}_${mymodule}.resource ADD column keywords tsvector;
-- create index idx_keyword_full_text on ${myuniversity}_${mymodule}.resource using gin(keywords);

CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.resource_set_keywords()
  returns trigger
AS $$
DECLARE
  keywords text;
BEGIN
--   NEW.keywords = to_tsvector('english', ${myuniversity}_${mymodule}.combine_keyword_element(NEW.jsonb));
  keywords = replace(${myuniversity}_${mymodule}.combine_keyword_element(NEW.jsonb), '"', ' ');
  NEW.jsonb = jsonb_set(NEW.jsonb, '{keywords}', to_jsonb(keywords), true);
--   NEW.keywords = to_tsvector('English', NEW.jsonb::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- alter function ${myuniversity}_${mymodule}.resource_set_keywords() owner to folio;

CREATE trigger set_resource_keywords_trigger
  BEFORE INSERT OR UPDATE
  ON resource
  FOR each row
EXECUTE PROCEDURE resource_set_keywords();

-- Create a view of all tags
CREATE OR REPLACE VIEW ${myuniversity}_${mymodule}.tag_view AS
  SELECT DISTINCT jsonb_array_elements_text(resource.jsonb->'tags'->'tagList') tag
  FROM ${myuniversity}_${mymodule}.resource;
GRANT SELECT ON ${myuniversity}_${mymodule}.tag_view TO ${myuniversity}_${mymodule};


