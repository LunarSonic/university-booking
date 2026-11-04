import {readFile} from 'node:fs/promises';
import {test} from 'node:test';
import {initializeTestEnvironment, assertFails} from '@firebase/rules-unit-testing';
import {doc, getDoc, setDoc} from 'firebase/firestore';
test('Server-only collections reject direct anonymous and authenticated access', async () => {
  const env = await initializeTestEnvironment({projectId:'demo-booking-rules',firestore:{
    host:'127.0.0.1',port:8089,rules:await readFile('../../firestore.rules','utf8')}});
  try {
    for (const context of [env.unauthenticatedContext(),env.authenticatedContext('student')]) {
      for (const collection of ['bookings','services','bookingChanges','bookingChangeDaily','counters','checkouts']) {
        const ref = doc(context.firestore(),collection,'test');
        await assertFails(getDoc(ref));
        await assertFails(setDoc(ref,{value:1}));
      }
    }
  } finally { await env.cleanup(); }
});
