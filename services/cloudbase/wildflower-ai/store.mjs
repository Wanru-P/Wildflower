/** A single CloudBase document replaces the Worker Durable Object. No prompts or provider secrets are persisted. */
export class CloudBaseStore {
  constructor(database, collection = 'wildflower_ai_limits', timeoutMs = 2000) { this.database = database; this.collection = collection; this.timeoutMs = timeoutMs; }
  async transact(action) {
    const operation = this.database.runTransaction(async tx => {
      const document = tx.collection(this.collection).doc('global-v1');
      const response = await document.get();
      const value = Array.isArray(response.data) ? response.data[0] : response.data;
      if (!value) throw new Error('ledger_missing'); // Never silently reset the lifetime budget.
      const state = structuredClone(value); delete state._id;
      const result = action(state);
      await document.set(state);
      return result;
    });
    let timer;
    try {
      const transaction = await Promise.race([operation, new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error('ledger_timeout')), this.timeoutMs);
      })]);
      // Pinned @cloudbase/database 3.x returns the callback value directly.
      if (transaction === undefined) throw new Error('transaction_failed');
      return transaction;
    } finally { clearTimeout(timer); }
  }
}
