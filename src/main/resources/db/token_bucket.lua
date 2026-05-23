-- Redis Lua: Distributed Token Bucket
-- KEYS[1]: tokens key    KEYS[2]: timestamp key
-- ARGV[1]: rate (tokens/sec)  ARGV[2]: capacity
-- ARGV[3]: now (epoch millis) ARGV[4]: requested tokens
-- Returns: {allowed(0|1), remaining, retry_after_ms}

local tokens_key = KEYS[1]
local timestamp_key = KEYS[2]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local last_tokens = tonumber(redis.call("get", tokens_key))
if last_tokens == nil then last_tokens = capacity end

local last_time = tonumber(redis.call("get", timestamp_key))
if last_time == nil then last_time = now end

local delta = math.max(0, now - last_time)
local filled = math.min(capacity, last_tokens + (delta * rate / 1000))
local allowed = filled >= requested
local new_tokens = filled
local retry_after = 0

if allowed then
    new_tokens = filled - requested
else
    retry_after = math.ceil((requested - filled) / rate * 1000)
end

local fill_time = math.ceil(capacity / rate)
local ttl = math.max(fill_time * 2, 60)

redis.call("setex", tokens_key, ttl, new_tokens)
redis.call("setex", timestamp_key, ttl, now)

return {allowed and 1 or 0, math.floor(new_tokens), retry_after}
