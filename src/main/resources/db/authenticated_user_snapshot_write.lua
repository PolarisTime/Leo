-- Redis Lua: atomically write an authenticated-user snapshot and its index entry.
-- KEYS[1]: user snapshot key
-- KEYS[2]: snapshot index key
-- ARGV[1]: serialized snapshot
-- ARGV[2]: snapshot TTL in milliseconds
-- ARGV[3]: index TTL in milliseconds
-- ARGV[4]: user ID stored in the index

redis.call("set", KEYS[1], ARGV[1], "PX", tonumber(ARGV[2]))
redis.call("sadd", KEYS[2], ARGV[4])
redis.call("pexpire", KEYS[2], tonumber(ARGV[3]))
return 1
