import csv
import sys
from collections import Counter

def read_csv(filename: str):
    with open(filename, "r") as f:
        return list(csv.DictReader(f))

def print_stats(filename: str):
    rows = read_csv(filename)

    # Total events
#     events = Counter()
#     total = 0
#     for row in rows:
#         count = int(row["count"])
#         events[row["result"]] += count
#         total += count

#     print(f"Total events measured: {total}")
#     print(f"\tNull values returned: {events['0']} ({100*events['0']/total}%)")
#     print(f"\tNon-null values returned: {events['1']} ({100*events['1']/total}%)")
#     print(f"\tExceptions thrown: {events['2']} ({100*events['2']/total}%)")

    def print_summary(rows, prefix):
        num_total = sum(int(row["count"]) for row in rows)
        num_null = sum(int(row["count"]) for row in rows if row["result"] == '0')
        num_nonnull = sum(int(row["count"]) for row in rows if row["result"] == '1')
        num_thrown = sum(int(row["count"]) for row in rows if row["result"] == '2')
        print(f"{prefix}: {num_total}")
        print(f"\tNull values returned: {num_null} ({100*num_null/num_total}%)")
        print(f"\tNon-null values returned: {num_nonnull} ({100*num_nonnull/num_total}%)")
        print(f"\tExceptions thrown: {num_thrown} ({100*num_thrown/num_total}%)")
        return (num_total, num_null, num_nonnull, num_thrown)

    overall = print_summary(rows, "Total events measured")

    rows_with_null_fields = [row for row in rows if '0' in row["fields"]]
    null_fields = print_summary(rows_with_null_fields, "Events where the receiver has null fields")

    rows_with_nonnull_fields = [row for row in rows if '0' not in row["fields"]]
    nonnull_fields = print_summary(rows_with_nonnull_fields, "Events where the receiver has no null fields")

    rows_with_null_params = [row for row in rows if '0' in row["params"]]
    null_params = print_summary(rows_with_null_params, "Events where the receiver has null params")

    rows_with_nonnull_params = [row for row in rows if '0' not in row["params"]]
    nonnull_params = print_summary(rows_with_nonnull_params, "Events where the receiver has no null params")




#     rows_by_field_nullity = {}
#     buckets = 5
#     bucket_size = 1.0/buckets
#     for i in range(buckets):
#         bucket_min = i*bucket_size
#         bucket_max = bucket_min + bucket_size
#         in_bucket = [row for row in rows if ]

    # Null events
#     null_rows = [row for row in rows if row["result"] == '0']
#     num_null = sum(int(row["count"]) for row in null_rows)
#     num_null_with_null_params = sum(int(row["count"]) for row in null_rows if '0' in row["params"])
#     num_null_with_nonnull_params = sum(int(row["count"]) for row in null_rows if '0' not in row["params"])
#     print(f"When a null value was returned,")
#     print(f"\tOne or more parameter was null: {num_null_with_null_params} ({100*num_null_with_null_params/num_null}%)")
#     print(f"\tAll parameters were non-null: {num_null_with_nonnull_params} ({100*num_null_with_nonnull_params/num_null}%)")
#     print()
#     num_null_with_null_fields = sum(int(row["count"]) for row in null_rows if '0' in row["fields"])
#     num_null_with_nonnull_fields = sum(int(row["count"]) for row in null_rows if '0' not in row["fields"])
#     print(f"\tOne or more field was null: {num_null_with_null_fields} ({100*num_null_with_null_fields/num_null}%)")
#     print(f"\tAll fields were non-null: {num_null_with_nonnull_fields} ({100*num_null_with_nonnull_fields/num_null}%)")


if __name__ == "__main__":
    print_stats(sys.argv[1])
