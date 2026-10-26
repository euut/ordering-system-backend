-- KEYS: List of Redis stock keys (product:stock:SKU-A, product:stock:SKU-B)
-- ARGV: List of corresponding deduction quantities

-- Pre-check all items for sufficient inventory
for i = 1, #KEYS do
    local currentStock = tonumber(redis.call('GET', KEYS[i]))
    local requestedQty = tonumber(ARGV[i])

    if not currentStock or currentStock < requestedQty then
        return 0 -- Fail entire batch if any single item is out of stock
    end
end

-- Deduct stock across all items atomically
for i = 1, #KEYS do
    redis.call('DECRBY', KEYS[i], tonumber(ARGV[i]))
end

return 1