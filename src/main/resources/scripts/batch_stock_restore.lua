-- KEYS: List of Redis stock keys
-- ARGV: List of corresponding restoration quantities

for i = 1, #KEYS do
    redis.call('INCRBY', KEYS[i], tonumber(ARGV[i]))
end

return 1