# rdr

A handy cli interface to searching and opening books from your [calibre](https://calibre-ebook.com/) library or recently read titles from your library.

## Installation

Download binaries from releases. Building from source requires oracles new AOT compiler for jvm based languages [graal](http://www.graalvm.org) Be advised that it is extremely memory hungry.

## Usage

### Actions

  -q [query]     => pass query with the same syntax as calibredb or calibregui accepts

  -l             => open the last book read

  -r             => filter the most recent 30 distinct books opened via rdr via rofi or dmenu

  query string   => If a string is passed in without -q -h -l -r specified it is treated as a query

  -o [file]      => open with default reader and record in recent reads if part of a calibre library

  -S [options]   => save options passed to disk
  
### Options

  -p [list]      => list of formats in order of preference eg pdf,epub,mobi

  -k [number]    => number of recent reads to keep

  --port PORT

  --server URL

  --user USER

  --password PASSWORD"
  
  The recommended usage is to pass the desired defaults with -S then omit them on future invocations.
  Example:
  
  rdr -S -L /path/to/library -p pdf,epub,mobi -k 10 -p 8090 --user me --password hunter2
  
  then 
  
  rdr -q query string here

## Please Note

  If you expect this program to work while Calibre's gui is running you must enable Calibre's content server and pass in login parameters if applicable. The content server is disabled by default. You can enable it in Calibre's preferences menu. By default it will serve from localhost on port 8080 which is also the default for this program. If you haven't changed these parameters it is sufficient to simply enable the server. The reason for this limitation is technical. As this application wraps calibredb it inherits its limitations. When Calibre is running it can't access the database directly as both the Calibre UI and calibredb could in theory modify the database thus it can only work if it is able to communicate with the running Calibre content server.

## Example

[example video](https://www.youtube.com/watch?v=RuWe0uhzrXE&)

### Limitations
Rdr doesn't actually support remote calibre servers yet as it presently just uses this feature to work around the fact that calibredb wont work locally without talking to the calibre content server.  It could probably be trivially expanded to fetch and then display remote books in the future.

## License

GPL 3.0 or later.
Copyright © 2019 Michael Rose
