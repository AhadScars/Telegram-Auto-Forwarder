# Auto Post

Android app jo aapke Telegram account se login karti hai. Aap kai source channels ya groups choose karte ho, ek target channel choose karte ho, aur Auto post on karte ho. Uske baad source par naya post aate hi woh target par copy ho jata hai.

## Pehle yeh karo

1. Phone par [my.telegram.org](https://my.telegram.org) kholo aur apne number se login karo.
2. **API development tools** me ek app banao.
3. `api_id` aur `api_hash` copy karke rakho. Yeh sirf aapke phone par save hota hai.

## Android Studio

1. `telegram-forwarder` folder Android Studio me open karo.
2. Gradle sync hone do.
3. Apna phone USB se connect karo (USB debugging on) aur **Run** dabao.

## App me

1. API ID aur API hash daalo.
2. Phone number country code ke saath likho, jaise `+9198XXXXXXXX`.
3. Telegram code daalo. 2FA ho to password bhi.
4. Jin channels se copy karna hai un par **Source** dabao. Ek se zyada ho sakte hain.
5. Jis channel par post karna hai us par **Target** dabao. Aap us channel me post kar sakte hone chahiye (admin).
6. **Auto post** on karo.
7. **Battery restriction hatao**, warna phone background me app ko band kar deta hai.
8. Notification "Auto post on hai" dikhti rehni chahiye.

**Forwarded from chhupao** on hai to target par naya post jayega, forward tag ke bina. Off karoge to Telegram ka normal forward dikhega.

Sirf woh posts copy hote hain jo switch on karne ke baad aate hain. Purani history copy nahi hoti.

Protected channel se copy fail ho sakta hai. Fail hone par neeche log me Telegram ki error dikhegi.
