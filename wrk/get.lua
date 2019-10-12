counter = 0

request = function()
    wrk.method = "GET"
    counter = counter + 1
    return wrk.format(nil, path)
end