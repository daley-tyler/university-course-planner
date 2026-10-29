-- =============================================================
--  V1__init_schema.sql
--  Course Planner - Unified Initial Schema (Text-based)
-- =============================================================

-- =============================================================
-- COURSE CATALOG
-- =============================================================

CREATE TABLE subject (
                         id SERIAL PRIMARY KEY,
                         code TEXT NOT NULL UNIQUE,
                         name TEXT NOT NULL
);

CREATE TABLE course (
                        id BIGSERIAL PRIMARY KEY,
                        subject_id INT NOT NULL REFERENCES subject(id),
                        number TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        prereq_text TEXT,
                        coreq_text TEXT,
                        attribute_text TEXT,
                        restriction_text TEXT,
                        credits NUMERIC(4,1) NOT NULL,
                        normalized BOOLEAN NOT NULL DEFAULT FALSE,
                        UNIQUE (subject_id, number)
);

CREATE TABLE attribute (
                           id SERIAL PRIMARY KEY,
                           code TEXT NOT NULL UNIQUE,
                           name TEXT NOT NULL,
                           category TEXT
);

CREATE TABLE course_attribute (
                                  course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
                                  attribute_id INT NOT NULL REFERENCES attribute(id) ON DELETE CASCADE,
                                  PRIMARY KEY (course_id, attribute_id)
);

-- =============================================================
-- COURSE PREREQUISITES / COREQUISITES
-- =============================================================

CREATE TABLE course_prerequisite_group (
                                           id BIGSERIAL PRIMARY KEY,
                                           course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
                                           kind TEXT NOT NULL CHECK (kind IN ('PREREQUISITE','COREQUISITE')),
                                           operator TEXT NOT NULL DEFAULT 'AND',
                                           description TEXT
);

CREATE TABLE course_prerequisite_course (
                                            id BIGSERIAL PRIMARY KEY,
                                            group_id BIGINT NOT NULL REFERENCES course_prerequisite_group(id) ON DELETE CASCADE,
                                            required_course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE RESTRICT,
                                            min_grade TEXT,
                                            concurrent_allowed BOOLEAN DEFAULT FALSE
);

-- =============================================================
-- STRUCTURED COURSE RESTRICTIONS
-- =============================================================

CREATE TABLE course_restriction (
                                    id BIGSERIAL PRIMARY KEY,
                                    course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE CASCADE,
                                    restriction_type TEXT NOT NULL CHECK (
                                        restriction_type IN (
                                                             'MAJOR','COLLEGE','CAMPUS','LEVEL','DEGREE',
                                                             'PROGRAM','DEPARTMENT','COHORT','ATTRIBUTE','CLASS'
                                            )
                                        ),
                                    mode TEXT NOT NULL CHECK (mode IN ('ONLY','EXCEPT')),
                                    value TEXT NOT NULL,
                                    CONSTRAINT uq_course_restriction UNIQUE (course_id, restriction_type, mode, value)
);

CREATE INDEX idx_course_restriction_course_id ON course_restriction(course_id);
CREATE INDEX idx_course_restriction_type_value ON course_restriction(restriction_type, value);

-- =============================================================
-- PROGRAMS & CATALOG-YEAR VERSIONS
-- =============================================================

CREATE TABLE program (
                         id BIGSERIAL PRIMARY KEY,
                         code TEXT NOT NULL UNIQUE,
                         name TEXT NOT NULL,
                         program_type TEXT NOT NULL,
                         college TEXT,
                         degree_type TEXT
);

CREATE TABLE program_catalog_version (
                                         id BIGSERIAL PRIMARY KEY,
                                         program_id BIGINT NOT NULL REFERENCES program(id) ON DELETE CASCADE,
                                         catalog_year INT NOT NULL,
                                         version_label TEXT,
                                         effective_start DATE,
                                         effective_end DATE,
                                         UNIQUE (program_id, catalog_year)
);

-- =============================================================
-- REQUIREMENT TREE (PER CATALOG VERSION)
-- =============================================================

CREATE TABLE req_node (
                          id BIGSERIAL PRIMARY KEY,
                          pcv_id BIGINT NOT NULL REFERENCES program_catalog_version(id) ON DELETE CASCADE,
                          parent_id BIGINT REFERENCES req_node(id) ON DELETE CASCADE,
                          name TEXT NOT NULL,
                          kind TEXT NOT NULL CHECK (
                              kind IN (
                                       'ALL_OF','ANY_OF','N_OF','CREDIT_AT_LEAST',
                                       'LEAF_COURSES','LEAF_ATTRIBUTES','LEAF_QUERY',
                                       'LEVEL_AT_LEAST','GRADE_MIN','GPA_AT_LEAST',
                                       'RESIDENCY_MIN','NOTE_ONLY','TRACK_OPTION',
                                       'EXPERIENCE','CONCURRENCY_BLOCK','DOUBLE_COUNT_RULE',
                                       'COURSE_CAP'
                                  )
                              ),
                          order_index INT DEFAULT 0,
                          n_required INT,
                          credit_min NUMERIC(5,1),
                          credit_max NUMERIC(5,1),
                          notes TEXT,
                          level_min SMALLINT,
                          level_cap100 NUMERIC(5,1),
                          level_cap200 NUMERIC(5,1),
                          level_cap300 NUMERIC(5,1),
                          level_cap400 NUMERIC(5,1),
                          level_cap100_courses SMALLINT,
                          level_cap200_courses SMALLINT,
                          level_cap300_courses SMALLINT,
                          level_cap400_courses SMALLINT,
                          grade_min TEXT,
                          residency_credits NUMERIC(5,1),
                          double_count_allowed BOOLEAN DEFAULT TRUE,
                          double_count_cap NUMERIC(5,1),
                          footnote_text TEXT,
                          source_url TEXT,
                          source_ref TEXT
);

CREATE TABLE req_node_course_option (
                                        req_node_id BIGINT NOT NULL REFERENCES req_node(id) ON DELETE CASCADE,
                                        course_id BIGINT NOT NULL REFERENCES course(id) ON DELETE RESTRICT,
                                        PRIMARY KEY (req_node_id, course_id)
);

CREATE TABLE req_node_attribute_option (
                                           req_node_id BIGINT NOT NULL REFERENCES req_node(id) ON DELETE CASCADE,
                                           attribute_id INT NOT NULL REFERENCES attribute(id) ON DELETE RESTRICT,
                                           PRIMARY KEY (req_node_id, attribute_id)
);

CREATE TABLE req_node_query (
                                req_node_id BIGINT PRIMARY KEY REFERENCES req_node(id) ON DELETE CASCADE,
                                query_json JSONB NOT NULL
);

-- =============================================================
-- PER-PCV POLICY/PROGRESSION NOTES
-- =============================================================

CREATE TABLE program_catalog_policy (
    id BIGSERIAL PRIMARY KEY,
    pcv_id BIGINT NOT NULL UNIQUE REFERENCES program_catalog_version(id) ON DELETE CASCADE,
    grade_min_text TEXT,
    grade_min_courses TEXT,
    notes TEXT,
    source_url TEXT,
    scraped_at TIMESTAMP DEFAULT now()
);

-- =============================================================
-- USERS & COURSE PLANS
-- =============================================================

CREATE TABLE app_user (
                          id BIGSERIAL PRIMARY KEY,
                          email TEXT UNIQUE,
                          display_name TEXT
);

CREATE TABLE course_plan (
                             id BIGSERIAL PRIMARY KEY,
                             user_id BIGINT REFERENCES app_user(id) ON DELETE CASCADE,
                             name TEXT NOT NULL,
                             created_at TIMESTAMP DEFAULT now(),
                             updated_at TIMESTAMP DEFAULT now()
);

-- =============================================================
-- PLAN -> PROGRAM LINKS
-- =============================================================

CREATE TABLE plan_program_degree (
                                     id BIGSERIAL PRIMARY KEY,
                                     plan_id BIGINT NOT NULL REFERENCES course_plan(id) ON DELETE CASCADE,
                                     pcv_id BIGINT NOT NULL REFERENCES program_catalog_version(id) ON DELETE RESTRICT,
                                     UNIQUE (plan_id, pcv_id)
);

CREATE TABLE plan_program_general_education (
                                         plan_id BIGINT PRIMARY KEY REFERENCES course_plan(id) ON DELETE CASCADE,
                                         pcv_id BIGINT NOT NULL REFERENCES program_catalog_version(id) ON DELETE RESTRICT
);

-- =============================================================
-- SEMESTERS & COURSES IN EACH PLAN
-- =============================================================

CREATE TABLE plan_semester (
                               id BIGSERIAL PRIMARY KEY,
                               plan_id BIGINT NOT NULL REFERENCES course_plan(id) ON DELETE CASCADE,
                               name TEXT NOT NULL,
                               sort_order INT DEFAULT 0
);

CREATE TABLE plan_course (
                             id BIGSERIAL PRIMARY KEY,
                             plan_id BIGINT NOT NULL REFERENCES course_plan(id) ON DELETE CASCADE,
                             course_id BIGINT REFERENCES course(id),
                             custom_title TEXT,
                             custom_credits NUMERIC(4,1),
                             status TEXT NOT NULL CHECK (status IN ('REMAINING','COMPLETED','IN_SEMESTER')),
                             semester_id BIGINT REFERENCES plan_semester(id) ON DELETE CASCADE,
                             notes TEXT
);

-- =============================================================
-- VALIDATION
-- =============================================================

CREATE TABLE validation_run (
                                id BIGSERIAL PRIMARY KEY,
                                plan_id BIGINT NOT NULL REFERENCES course_plan(id) ON DELETE CASCADE,
                                ran_at TIMESTAMP DEFAULT now()
);

CREATE TABLE validation_assignment (
                                       id BIGSERIAL PRIMARY KEY,
                                       run_id BIGINT NOT NULL REFERENCES validation_run(id) ON DELETE CASCADE,
                                       pcv_id BIGINT NOT NULL REFERENCES program_catalog_version(id) ON DELETE CASCADE,
                                       req_node_id BIGINT NOT NULL REFERENCES req_node(id) ON DELETE CASCADE,
                                       plan_course_id BIGINT NOT NULL REFERENCES plan_course(id) ON DELETE CASCADE,
                                       credits_applied NUMERIC(4,1)
);

CREATE TABLE validation_unmet (
                                  id BIGSERIAL PRIMARY KEY,
                                  run_id BIGINT NOT NULL REFERENCES validation_run(id) ON DELETE CASCADE,
                                  pcv_id BIGINT NOT NULL REFERENCES program_catalog_version(id) ON DELETE CASCADE,
                                  req_node_id BIGINT NOT NULL REFERENCES req_node(id) ON DELETE CASCADE,
                                  details JSONB
);

-- =============================================================
-- SCRAPE LOG
-- =============================================================

CREATE TABLE scrape_log (
                            id BIGSERIAL PRIMARY KEY,
                            term TEXT NOT NULL UNIQUE,
                            scraped_at TIMESTAMP DEFAULT now()
);
