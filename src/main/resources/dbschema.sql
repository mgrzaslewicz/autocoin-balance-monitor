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
