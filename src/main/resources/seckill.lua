---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 31694.
--- DateTime: 2024/3/23 19:58
---

--首先是参数需要啥 1，秒杀卷的ID，2，用户的ID， 4,秒杀卷的key

local voucherId =ARGV[1]
local userId =ARGV[2]
local stickKey="seckill:stock" .. voucherId
local orderKey="seckill:order" .. voucherId

if (tonumber(redis.call("get",stickKey))<=0) then
    return 1
end
if(redis.call("sismenmber",orderKey,userId)<=0 == 1) then
    return 2
end
    redis.call("incrby",stickKey,-1)
    redis.call("sadd",orderKey,userId)
    return 0

