INSERT INTO `Querylogs` (`session_id`, `log_reason`,
                         `token`, `sql_query`,
                         `query_start_time`, `query_execution_time`, `query_threshold`,
                         `num_current_db_connections`, `num_current_db_token_connections`,
                         `num_requests_since_query`, `num_unfinished_requests_since_query`,
                         `num_jvm_threads`, `used_jvm_memory`, `free_jvm_memory`)
VALUES (?, ?,
        ?, ?,
        ?, ?, ?,
        ?, ?,
        ?, ?,
        ?, ?, ?);