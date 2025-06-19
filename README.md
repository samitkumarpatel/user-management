# user-mamagement

a repo for user management. It pull data from json-placeholder api + do a lookup on the db.

```shell
# Crete new user
http :8080/user name='Samit Kumar Patel' username='samit' email='samitkumar.patel@abc.net'

# Get all users
http :8080/user

# Get a user by id
http :8080/user/1
```