-- use with SQLite

CREATE TABLE "source_list" (
	"source_rpath"	TEXT NOT NULL,
	"source_size"	INTEGER,
	"source_date"	INTEGER,
	"digest"		INTEGER,
	PRIMARY KEY("source_rpath")
);
