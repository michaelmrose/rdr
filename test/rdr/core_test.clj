(ns rdr.core-test
  (:require [clojure.test :refer :all]
            [rdr.core :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 0))))
;; to be implimented a series of test opertions
;; run rdr with no config directory it should be created
;; run rdr-r with an empty recent file it shouldn't crash
;; run rdr -S to set defaults and look at settings file after
;; run a series of rdr queries to be expanded on
;; specifically test that -l and -r work when metadata has been edited
;; should work equally well with the default library and a manually specified one
;; should work with calibre running or not
;; ensure that results are correct and recent file contains the list of files chosen
