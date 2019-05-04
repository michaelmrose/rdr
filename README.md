# rdr

A handy cli interface to searching and opening books from your calibre library or recently read titles from your library.

## Installation

Download from releases. Building requires oracles new AOT compiler for jvm based languages [graal](http://www.graalvm.org) Be advised that it is extremely memory hungry.

## Usage

At present

-q [query] wherein query can be anything that calibre would accept. Searches library for items matching query narrowing down selection with rofi or dmenu and opens choice. This is intended to be with the reader of choice but at present it is hard coded to zathura.

-r chose from the 30 most recently read titles from calibre library. Please note that recent reads are recorded by the app not calibre.

-l open most recently opened book

-o [file] open file then record metadata from calibre in recent reads rdr -o is intended to be made the default reader for ebook files so that opening books properly records them in recent reads

See rdr.org for coming development.

## Options

## Examples

### Bugs

Calibre returns incorrect results when you talk to the running process so this wont work with calibre running until this is fixed. Specifically in older versions of calibre calibredb would return fine with calibre running. Newish versions refuse to operate if calibre is running and require you to pass the server as an option to calibredb. The current calibre process handles queries from calibredb to ensure their is only one process talking to calibre. Right now it returns the text "Epub" instead of "/absolute/path/to/somebook.epub" for formats when you pass --with-library=http://localhost:8080

This is true of at least 3.41.3 whereas 2.55 will gladly return without --with-library.  The desired behavior it would seem to me would be to return without issue for operations that don't require writing. I will create an issue on calibre's bug tracker and see what can be done.

If the bug is fixed I can test for version before querying if required.
...


## License

GPL 3.0 or later.
Copyright © 2019 Michael Rose
