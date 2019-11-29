#Yandex-tank

#### 1. ammo with unique keys PUT's 
####(line(1, 2000, 3m))
[line1](https://overload.yandex.net/232766). Stop to be stable on ~770 rps


####(line(1, 430, 1m) const(430, 4m))
[const1](https://overload.yandex.net/232767).  == 430 rps. 98% ~~ 57 ms.

#### 2. ammo with 10% existed keys PUT's 
####(line(1, 2000, 3m))
[line2](https://overload.yandex.net/232769). stop to be stable on ~856 rps

####(line(1, 600, 1m) const(600, 4m))
[const2](https://overload.yandex.net/232772).  856 - 856*0.3 ~= 600 rps. 98% ~~ 65 ms.


#### 3. ammo with exit Val keys GET's 
####(line(1, 2000, 3m))
[line3](https://overload.yandex.net/232773). stop to be stable on ~869 rps

####(line(1, 590, 1m) const(590, 4m))
[const3](https://overload.yandex.net/232775).  869 - 869*0.3 ~= 590 rps. 98% ~~ 60 ms.


#### 4. ammo with last exit Val keys GET's 
####(line(1, 2000, 3m))
[line4](https://overload.yandex.net/232776). stop to be stable on ~801 rps

####(line(1, 500, 1m) const(500, 4m))
[const4](https://overload.yandex.net/232778).  801 - 801*0.3 ~= 500 rps. 98% ~~ 70 ms.


#### 5. ammo with mixed GET's && PUT's 
####(line(1, 2000, 3m))
[line5](https://overload.yandex.net/232779). stop to be stable on ~901 rps

####(line(1, 500, 1m) const(500, 4m))
[const5](https://overload.yandex.net/232781).  900 - 900*0.3 ~= 500 rps. 98% ~~  55 ms.
