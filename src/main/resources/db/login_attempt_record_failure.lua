-- Redis Lua: atomically count a login failure and apply the lock threshold.
-- KEYS[1]: failure counter key
-- KEYS[2]: lock key
-- ARGV[1]: current timestamp in milliseconds
-- ARGV[2]: failure window in seconds
-- ARGV[3]: maximum failures before locking
-- ARGV[4]: lock duration in seconds

local failure_count = redis.call("incr", KEYS[1])
if failure_count == 1 then
    redis.call("expire", KEYS[1], tonumber(ARGV[2]))
end

if failure_count >= tonumber(ARGV[3]) then
    redis.call("set", KEYS[2], ARGV[1], "EX", tonumber(ARGV[4]))
    redis.call("del", KEYS[1])
end

return failure_count
