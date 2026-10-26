local stockKey = KEYS[1]
local quantity = tonumber(ARGV[1])

local currentStock = tonumber(redis.call('GET', stockKey))

if not currentStock then
    return -1
end

if currentStock < quantity then
    return 0
end

redis.call('DECRBY', stockKey, quantity)
return 1