

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

-- many to many between entry and category. Each entry can have multiple categorys. Each category may be used
-- on multiple entries.
create table etocat (
        eid integer, -- fk to entry.id
        cid integer  -- fk to category.id
);


