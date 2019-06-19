# rdr

A handy cli interface to searching and opening books from your calibre library or recently read titles from your library.

## Installation

Download from releases. Building requires oracles new AOT compiler for jvm based languages [graal](http://www.graalvm.org) Be advised that it is extremely memory hungry.

## Usage

### Actions

  -q [query]     => pass query with the same syntax as calibredb or calibregui accepts

  -l             => open the last book read

  -r             => filter the most recent 30 distinct books opened via rdr via rofi or dmenu

  a query string => same as -q a query string

  -o [file]      => open with default reader and record in recent reads if part of a calibre library
  
### Options

  -p [list]      => list of formats in order of preference eg pdf,epub,mobi

  -k [number]    => number of recent reads to keep

  --port PORT

  --server URL

  --user USER

  --password PASSWORD"
  
  -S [options]   => save options to disk will not complete other operations. -S --port 8090 -q somequery will save but will not submit somequery to calibredb.
  
  If you don't want to keep passing the above options consider running rdr -S option1 value option2 value to save said options then you may omit them from future invocations.

## Please Note

  Please note that calibres content server must be running for this program to work while calibre is running as it must communicate with the
  content server process instead of directly using calibredb to examine the database. If neccesary please specify the server,port,username,and password.
  The default is http://localhost:8080 with no password

## Example

[example video](https://www.youtube.com/watch?v=RuWe0uhzrXE&)

### Limitations
Rdr doesn't actually support remote calibre servers yet as it presently just uses this feature to work around the fact that calibredb wont work locally without talking to the calibre content server.  It could probably be trivially expanded to fetch and then display remote books in the future.

## License

GPL 3.0 or later.
Copyright © 2019 Michael Rose
