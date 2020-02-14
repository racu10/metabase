(ns metabase.query-processor.middleware.resolve-referenced-test
  (:require [clojure.test :refer :all]
            [metabase.models
             [card :refer [Card]]
             [database :refer [Database]]]
            [metabase.query-processor.middleware.resolve-referenced :as referenced]
            [metabase.query-processor.store :as qp.store]
            [metabase.test.data :as data]
            [toucan.util.test :as tt])
  (:import clojure.lang.ExceptionInfo))

(deftest tags-referenced-cards-lookup-test
  (testing "returns Card instances from raw query"
    (tt/with-temp* [Card [c1 {}]
                    Card [c2 {}]]
      (is (= [c1 c2]
             (#'referenced/tags-referenced-cards
              {:native
               {:template-tags
                {"tag-name-not-important1" {:type :card
                                            :card (:id c1)}
                 "tag-name-not-important2" {:type :card
                                            :card (:id c2)}}}}))))))

(deftest resolve-card-resources-test
  (testing "resolve stores source table from referenced card"
    (tt/with-temp Card [mbql-card {:dataset_query (data/mbql-query venues
                                                    {:filter [:< [:field-id $price] 3]})}]
      (let [query {:database (data/id)
                   :native   {:template-tags
                              {"tag-name-not-important1" {:type :card
                                                          :card (:id mbql-card)}}}}]
        (qp.store/with-store
          (qp.store/fetch-and-store-database! (data/id))

          (is (thrown-with-msg? Exception #"Error: Table [0-9]+ is not present in the Query Processor Store\."
                                (qp.store/table (data/id :venues))))
          (is (thrown-with-msg? Exception #"Error: Field [0-9]+ is not present in the Query Processor Store\."
                                (qp.store/field (data/id :venues :price))))

          (is (= query
                 (#'referenced/resolve-referenced-card-resources* query)))

          (is (some? (qp.store/table (data/id :venues))))
          (is (some? (qp.store/field (data/id :venues :price)))))))))

(deftest referenced-query-from-different-db-test
  (testing "fails on query that references a native query from a different database"
    (tt/with-temp* [Database [outer-query-db]
                    Card     [card {:dataset_query
                                    {:database (data/id)
                                     :type     :native
                                     :native   {:query "SELECT 1 AS \"foo\", 2 AS \"bar\", 3 AS \"baz\""}}}]]
      (let [card-id     (:id card)
            card-query  (:dataset_query card)
            tag-name    (str "#" card-id)
            query-db-id (:id outer-query-db)
            query       {:database query-db-id ; Note db-1 is used here
                         :type     :native
                         :native   {:query         (format "SELECT * FROM {{%s}} AS x" tag-name)
                                    :template-tags {tag-name ; This tag's query is from the test db
                                                    {:id tag-name, :name tag-name, :display-name tag-name,
                                                     :type "card", :card card-id}}}}]
        (is (= {:referenced-query     card-query
                :expected-database-id query-db-id}
               (try
                (#'referenced/check-query-database-id= card-query query-db-id)
                (catch ExceptionInfo exc
                  (ex-data exc)))))

        (is (nil? (#'referenced/check-query-database-id= card-query (data/id))))

        (is (= {:referenced-query     card-query
                :expected-database-id query-db-id}
               (try
                (#'referenced/resolve-referenced-card-resources* query)
                (catch ExceptionInfo exc
                  (ex-data exc))))))))

  (testing "fails on query that references an MBQL query from a different database"
    (tt/with-temp* [Database [outer-query-db]
                    Card     [card {:dataset_query (data/mbql-query venues
                                                     {:filter [:< [:field-id $price] 3]})}]]
      (let [card-id     (:id card)
            card-query  (:dataset_query card)
            tag-name    (str "#" card-id)
            query-db-id (:id outer-query-db)
            query       {:database query-db-id ; Note outer-query-db is used here
                         :type     :native
                         :native   {:query         (format "SELECT * FROM {{%s}} AS x" tag-name)
                                    :template-tags {tag-name ; This tag's query is from the test db
                                                    {:id tag-name, :name tag-name, :display-name tag-name,
                                                     :type "card", :card card-id}}}}]
        (is (= {:referenced-query     card-query
                :expected-database-id query-db-id}
               (try
                (#'referenced/check-query-database-id= card-query query-db-id)
                (catch ExceptionInfo exc
                  (ex-data exc)))))

        (is (nil? (#'referenced/check-query-database-id= card-query (data/id))))

        (is (= {:referenced-query     card-query
                :expected-database-id query-db-id}
               (try
                (#'referenced/resolve-referenced-card-resources* query)
                (catch ExceptionInfo exc
                  (ex-data exc)))))))))