---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by 31694.
--- DateTime: 2024/3/23 14:41
---

--获取到线程标识与锁的标识是否一致
if(redis.call('get',KET[1])==ARGV[1]) then
    --释放锁del key
    return redis.call('del',KEY[1])
end
return 0
