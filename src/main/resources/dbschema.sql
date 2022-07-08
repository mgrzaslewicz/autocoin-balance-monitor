--liquibase formatted sql

--changeset mgrzaslewicz:1-create-schema
create table user_blockchain_wallet
(
    id              int primary key,
    user_account_id varchar(36)  not null,
    currency        varchar(255) not null,
    wallet_address  varchar(255) not null,
    description     varchar(255)
);
