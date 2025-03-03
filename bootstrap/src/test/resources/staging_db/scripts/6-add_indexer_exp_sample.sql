UPDATE indexer_exp.contributions
SET github_status = 'DRAFT'
WHERE id in ('b66cd16a35e0043d86f1850eb9ba6519d20ff833394f7516b0842fa2f18a5abf',
             '7b076143d6844660494a112d2182017a367914577b14ed562250ef1751de6547');

UPDATE indexer_exp.github_pull_requests
SET status = 'DRAFT'
WHERE repo_id = 493591124
  AND number = 17;


CREATE TABLE indexer_exp.github_repo_languages
(
    repo_id         BIGINT    NOT NULL,
    language        TEXT      NOT NULL,
    line_count      BIGINT    NOT NULL,
    tech_created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    tech_updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (repo_id, language)
);

INSERT INTO indexer_exp.github_repo_languages (repo_id, language, line_count)
SELECT gr.id,
       l.key::text,
       l.value::bigint
FROM indexer_exp.github_repos gr
         CROSS JOIN jsonb_each(gr.languages) l;
