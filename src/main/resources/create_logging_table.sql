DROP TABLE IF EXISTS `Querylogs`;

CREATE TABLE `Querylogs` (
    `id` int(11) NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT NOT NULL,
    `log_reason` text NOT NULL,
    `token` text NOT NULL,
    `sql_query` text NOT NULL,
    `query_start_time` timestamp NOT NULL,
    `query_execution_time` BIGINT,
    `query_threshold` BIGINT,
    `num_current_db_connections` int NOT NULL,
    `num_current_db_token_connections` int NOT NULL,
    `num_requests_since_query` int,
    `num_unfinished_requests_since_query` int,
    `num_jvm_threads` int NOT NULL,
    `used_jvm_memory` BIGINT NOT NULL,
    `free_jvm_memory` BIGINT NOT NULL,
    PRIMARY KEY (`id`)
);