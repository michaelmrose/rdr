# rdr

A handy cli interface to searching and opening books from your calibre library or recently read titles from your library.

## Installation

Download from releases. Building requires oracles new AOT compiler for jvm based languages [graal](http://www.graalvm.org) Be advised that it is extremely memory hungry.

## Usage

At present

-p [list]      => list of formats in order of preference eg pdf,epub,mobi
-S [options]   => save options to disk
-k [number]    => number of recent reads to keep
-q [query]     => pass query with the same syntax as calibredb or calibregui accepts
-r             => filter the most recent 30 distinct books opened via rdr via rofi or dmenu
-l             => open the last book read
-o [file]      => open with default reader and record in recent reads if part of a calibre library
a query string => same as -q a query string"

## Options

## Example

[example video](https://www.youtube.com/watch?v=RuWe0uhzrXE&)

### Bugs

## License

GPL 3.0 or later.
Copyright © 2019 Michael Rose
