(ns rdr.core
  (:require
   [clojure.java.shell :as ex]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [com.rpl.specter :as ?]
   [swiss.arrows :refer :all]
   [me.raynes.fs :as fs]
   [clojure.data.json :as json]
   [pandect.algo.adler32 :refer :all]
   [rdr.utils :as u]
   )
  (:gen-class))
(set! *warn-on-reflection* true)
