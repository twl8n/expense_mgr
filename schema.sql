

create table entry (
        id integer primary key autoincrement,
        date text,
        category integer,
        amount double,
        mileage integer,
        notes text
);


create table category (
        id integer primary key autoincrement,
        name text,
        desc text
);

create unique index idx1 on category(name);

-- select * from category;
-- 1|Fuel|
-- 2|Repair parts|
-- 3|Building Materials|
-- 4|Equipment|
-- 5|Seed|
-- 6|Fertilizer|
-- 7|Plants|
-- 8|Meetings|
-- 9|Office supplies|
-- 10|Vehicle repairs|
-- 11|Ag materials|
-- 12|Shop supplies|
-- 13|Protective gear|
-- 14|Equipment rental|
-- 15|personal - food|
-- 16|personal - unknown|
-- 17|Unknown|


insert into category (name) values ('personal - food');
insert into category (name) values ('personal - unknown');
insert into category (name) values ('Unknown');



-- many to many between entry and category. Each entry can have multiple categories. Each category may be used
-- on multiple entries.

create table etocat (
        eid integer, -- fk to entry.id
        cid integer  -- fk to category.id
);


