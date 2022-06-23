--liquibase formatted sql

--changeset mgrzaslewicz:1-create-schema
create table user_blockchain_wallet
(
    id              varchar(36) primary key,
    user_account_id varchar(36)  not null,
    currency        varchar(255) not null,
    wallet_address  varchar(255) not null,
    balance         numeric,
    description     varchar(1024),
    constraint uq_user_account_id_wallet_address unique (user_account_id, wallet_address)
);

--changeset mgrzaslewicz:2-add-exchange-wallet
create table user_exchange_wallet
(
    id               varchar(36) primary key,
    user_account_id  varchar(36)  not null,
    exchange         varchar(255) not null,
    exchange_user_id varchar(36)  not null,
    currency         varchar(255) not null,
    balance          numeric      not null,
    amount_in_orders numeric      not null,
    amount_available numeric      not null,
    constraint uq_user_account_id_exchange_exchange_user_id_currency unique (user_account_id, exchange, exchange_user_id, currency)
);

create table user_exchange_wallet_last_refresh
(
    id               varchar(36) primary key,
    user_account_id  varchar(36)  not null,
    exchange         varchar(255) not null,
    exchange_user_id varchar(36)  not null,
    error_message    varchar(1024),
    insert_time      timestamp,
    constraint uq_user_account_id_exchange_exchange_user_id unique (user_account_id, exchange, exchange_user_id)
);
