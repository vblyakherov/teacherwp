# Login API manual test

Attempted to call the production Login endpoint with the provided credentials using `curl`.

```
curl -i https://piggybank.torpedovrn.ru/Api/Login \
  -H 'Content-Type: application/json' \
  -d '{"username":"sidor","password":"123456789"}'
```

## Result

The request was blocked by the hosting environment's outbound proxy and returned `HTTP/1.1 403 Forbidden` before reaching the service, so the login API could not be exercised from this container.
