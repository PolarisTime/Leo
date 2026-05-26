-- Redis Lua: throttled session activity touch
-- KEYS[1]: session activity key
-- ARGV[1]: now epoch millis
-- ARGV[2]: online ttl millis
-- ARGV[3]: minimum write interval millis
-- Returns: 1 when value was updated, 0 when recent value was kept

local activity_key = KEYS[1]
local now = tonumber(ARGV[1])
local ttl_millis = tonumber(ARGV[2])
local min_interval = tonumber(ARGV[3])

local current = tonumber(redis.call("get", activity_key))
if current == nil or now - current >= min_interval then
    redis.call("set", activity_key, now, "PX", ttl_millis)
    return 1
end

return 0
