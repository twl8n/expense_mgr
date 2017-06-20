

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

-- many to many between entry and tag. Each entry can have multiple tags. Each tag may be used on multiple entries.
create table etocat (
        eid integer, -- fk to entry.id
        cid integer  -- fk to tag.id
);


