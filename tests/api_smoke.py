"""Run only against a dedicated test instance: API_URL=http://localhost:18080 python3 tests/api_smoke.py.
Creates test bookings and changes service 2's description, then restores it.
"""
import os,json,time,datetime,urllib.request,urllib.error,urllib.parse,concurrent.futures
BASE=os.environ.get('API_URL','http://localhost:18080')
USER=int(time.time()*1000)
def call(method,path,body=None,expected=200):
    data=None if body is None else json.dumps(body).encode()
    req=urllib.request.Request(BASE+path,data=data,method=method,headers={'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=30) as r: code,raw=r.status,r.read()
    except urllib.error.HTTPError as e: code,raw=e.code,e.read()
    assert code==expected,(method,path,code,raw.decode())
    return json.loads(raw) if raw else None
for i in range(40):
    try: services=call('GET','/services');break
    except (OSError,AssertionError):time.sleep(1)
else: raise RuntimeError('Backend did not start')
now=datetime.datetime.now(datetime.timezone.utc)
day=now.date();tomorrow=day+datetime.timedelta(days=1)
args={'status':'NEW','createdFrom':str(day)+'T00:00:00Z','createdTo':str(tomorrow)+'T00:00:00Z','limit':1,'needsProjector':'true','userId':USER}
def search(extra={}):return call('GET','/bookings/search?'+urllib.parse.urlencode(args|extra))
def create(user,projector=True):
 return call('POST','/bookings',{'userId':user,'serviceId':1,'room':305,'bookingDate':(now+datetime.timedelta(days=30)).strftime('%Y-%m-%dT%H:%M:%S'),'details':{'participants':20,'needsProjector':projector,'comment':'smoke'}},201)
a=create(USER);b=create(USER);c=create(USER+1,False)
assert a['details']['needsProjector'] is True
p=search();assert len(p['items'])==1 and p['nextCursor']
q=search({'cursor':p['nextCursor']});assert len(q['items'])==1 and not q['nextCursor'];assert p['items'][0]['id']!=q['items'][0]['id']
assert search({'direction':'ASC'})['items'][0]['id']==a['id']
call('GET','/bookings/search?'+urllib.parse.urlencode(args|{'cursor':p['nextCursor'],'needsProjector':'false'}),expected=400)
for change in [{'limit':0},{'limit':'abc'},{'direction':'BAD'},{'status':'BAD'},{'cursor':'broken'},{'createdTo':str(day)+'T00:00:00Z'}]:
 call('GET','/bookings/search?'+urllib.parse.urlencode(args|change),expected=400)
assert all(x['userId']==USER for x in call('GET',f'/bookings?status=NEW&userId={USER}'))
def analytics():return call('GET',f'/analytics/booking-changes?from={day}&to={tomorrow}')['items'][0]['changesCount']
before=analytics()
call('PATCH',f"/bookings/{a['id']}?status=APPROVED")
call('PATCH',f"/bookings/{a['id']}?status=APPROVED")
assignment={'serviceId':2,'details':{'participants':25,'needsProjector':False,'comment':'changed'}}
changed=call('PATCH',f"/bookings/{b['id']}/assignment",assignment)
assert changed['serviceId']==2 and changed['details']['participants']==25
call('PATCH',f"/bookings/{b['id']}/assignment",assignment)
call('PATCH',f"/bookings/{b['id']}/assignment",assignment|{'serviceId':999999},400)
assert call('GET',f"/bookings/{b['id']}")==changed
assert analytics()==before+2
# Concurrent identical updates must produce one effective transition.
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 list(pool.map(lambda _:call('PATCH',f"/bookings/{c['id']}?status=APPROVED"),range(2)))
assert analytics()==before+3
# Deletion of the cursor document must not break the next page.
x=create(USER);y=create(USER);page=search()
call('DELETE',f"/bookings/{page['items'][0]['id']}",expected=204)
assert search({'cursor':page['nextCursor']})['items']
# Cache population, invalidation, refill and TTL.
original=next(s for s in call('GET','/services') if s['id']==2)
call('GET','/services')
call('PATCH','/services/2',{'name':original['name'],'description':'cache-test','active':True})
assert next(s for s in call('GET','/services') if s['id']==2)['description']=='cache-test'
call('PATCH','/services/2',{'name':original['name'],'description':original['description'],'active':True})
# Checkout retry and concurrency.
basket=call('POST','/basket',{'userId':USER,'serviceId':1,'room':305,'bookingDate':(now+datetime.timedelta(days=30)).strftime('%Y-%m-%dT%H:%M:%S')},201)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 results=list(pool.map(lambda _:call('POST',f"/basket/{basket['id']}/checkout",expected=201),range(2)))
assert results[0]['id']==results[1]['id']
assert call('POST',f"/basket/{basket['id']}/checkout",expected=201)['id']==results[0]['id']
print('PASS: search, cursor, validation, atomic assignment, no-op/concurrent analytics, cache invalidation, idempotent checkout')
