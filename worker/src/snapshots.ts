import type { Env } from './types.ts';

const UA = 'SignalFeed/0.1 (personal non-commercial reader)';
async function json(url: string, headers: Record<string, string> = {}): Promise<any> {
  const response = await fetch(url, { headers: { 'User-Agent': UA, ...headers }, signal: AbortSignal.timeout(15_000) });
  if (!response.ok) throw Error(`${response.status} ${new URL(url).hostname}`);
  return response.json();
}
async function save(env: Env, id: string, payload: unknown): Promise<void> {
  await env.DB.prepare('INSERT INTO snapshots(id,payload,updated_at) VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at')
    .bind(id, JSON.stringify(payload), Date.now()).run();
}
type HistoryRound = { round: number; name: string; drivers: { id: string; name: string; points: number }[] };
export async function driverHistory(year: number, races: {round:number;name:string}[], lastRound: number, previous: HistoryRound[], teams = false): Promise<HistoryRound[]> {
  const history = new Map(previous.filter(row => row.round <= lastRound).map(row => [row.round, row]));
  for (const race of races.filter(race => race.round <= lastRound && (!history.has(race.round) || race.round === lastRound)).slice(0, 10)) {
    try {
      const data = await json(`https://api.jolpi.ca/ergast/f1/${year}/${race.round}/${teams ? "constructorstandings" : "driverstandings"}.json?limit=100`);
      const standings = data.MRData?.StandingsTable?.StandingsLists?.[0];
      if (Number(standings?.round) !== race.round) continue;
      const drivers = (teams ? standings.ConstructorStandings ?? [] : standings.DriverStandings ?? []).flatMap((row: any) => {
        const id = teams ? row.Constructor?.constructorId : row.Driver?.driverId;
        const name = teams ? row.Constructor?.name : [row.Driver?.givenName, row.Driver?.familyName].join(' ');
        return id && row.points != null && Number.isFinite(Number(row.points)) ? [{ id, name, points: Number(row.points) }] : [];
      });
      if (drivers.length) history.set(race.round, { round: race.round, name: race.name, drivers });
    } catch { /* Keep cached rounds; missing rounds are left as gaps in the chart. */ }
  }
  return [...history.values()].sort((a, b) => a.round - b.round);
}
export async function refreshF1(env: Env): Promise<void> {
  const year = new Date().getUTCFullYear();
  const base = `https://api.jolpi.ca/ergast/f1/${year}`;
  const [schedule, drivers, constructors, results] = await Promise.all([
    json(`${base}.json?limit=100`), json(`${base}/driverstandings.json`),
    json(`${base}/constructorstandings.json`), json(`${base}/last/results.json`)
  ]);
  const races = (schedule.MRData?.RaceTable?.Races ?? []).map((race: any) => ({
    round: Number(race.round), name: race.raceName, circuit: race.Circuit?.circuitName,
    locality: race.Circuit?.Location?.locality,
    startAt: Date.parse(`${race.date}T${race.time ?? '00:00:00Z'}`),
    sessions: ['FirstPractice','SecondPractice','ThirdPractice','SprintQualifying','Sprint','Qualifying']
      .flatMap(key => race[key] ? [{ name: key, startAt: Date.parse(`${race[key].date}T${race[key].time}`) }] : [])
  }));
  const next = races.find((race: any) => race.startAt > Date.now()) ?? null;
  const driverRows = drivers.MRData?.StandingsTable?.StandingsLists?.[0]?.DriverStandings ?? [];
  const constructorRows = constructors.MRData?.StandingsTable?.StandingsLists?.[0]?.ConstructorStandings ?? [];
  const result = results.MRData?.RaceTable?.Races?.[0] ?? null;
  const cached = await env.DB.prepare('SELECT payload FROM snapshots WHERE id=?').bind('f1').first<{payload:string}>();
  const previous = cached ? JSON.parse(cached.payload) : null;
  const history = await driverHistory(year, races, Number(result?.round ?? 0), previous?.year === year ? previous.driverHistory ?? [] : []);
  const constructorHistory = await driverHistory(year, races, Number(result?.round ?? 0), previous?.year === year ? previous.constructorHistory ?? [] : [], true);
  await save(env, 'f1', { year, races, next, drivers: driverRows.slice(0, 25).map((row: any) => ({
    position: Number(row.position), name: `${row.Driver?.givenName} ${row.Driver?.familyName}`,
    team: row.Constructors?.[0]?.name, points: Number(row.points)
  })), constructors: constructorRows.slice(0, 12).map((row: any) => ({
    position: Number(row.position), name: row.Constructor?.name, points: Number(row.points)
  })), lastResult: result ? { round: Number(result.round), name: result.raceName,
    finishers: (result.Results ?? []).slice(0, 10).map((row: any) => ({
      position: Number(row.position), name: `${row.Driver?.givenName} ${row.Driver?.familyName}`,
      team: row.Constructor?.name
    })) } : null, driverHistory: history, constructorHistory, attribution: 'Jolpica-F1 (CC BY-NC-SA 4.0)' });
}

export async function refreshAi(env: Env): Promise<void> {
  if (!env.AA_API_KEY) throw Error('AA_API_KEY is missing');
  const rows: any[] = [];
  let version: number | null = null;
  for (let page = 1; page <= 4; page++) {
    const response = await json(`https://artificialanalysis.ai/api/v2/language/models/free?page=${page}`,
      { 'x-api-key': env.AA_API_KEY });
    rows.push(...(response.data ?? []));
    version = response.intelligence_index_version ?? version;
    if (!response.pagination?.has_more) break;
  }
  const ranked = rows.filter(row => typeof row.evaluations?.artificial_analysis_intelligence_index === 'number')
    .sort((a, b) => b.evaluations.artificial_analysis_intelligence_index - a.evaluations.artificial_analysis_intelligence_index)
    .slice(0, 400);
  const today = new Date().toISOString().slice(0, 10);
  const previous = await env.DB.prepare('SELECT payload FROM snapshots WHERE id=?').bind('ai').first<{payload:string}>();
  const prior = previous ? JSON.parse(previous.payload) as { indexVersion?: number; models?: { id: string; rank: number }[] } : null;
  const priorRanks = prior?.indexVersion === version ? new Map(prior.models?.map(row => [row.id, row.rank])) : new Map<string, number>();
  const models = ranked.map((row, index) => ({ id: row.id, name: row.name, creator: row.model_creator?.name,
    rank: index + 1, change: priorRanks.has(row.id) ? priorRanks.get(row.id)! - (index + 1) : null,
    score: row.evaluations.artificial_analysis_intelligence_index,
    evaluations: Object.fromEntries(Object.entries(row.evaluations ?? {}).filter(([, value]) => typeof value === 'number' && Number.isFinite(value))),
    latency: row.performance?.median_time_to_first_token_seconds ?? null,
    inputPrice: row.pricing?.price_1m_input_tokens ?? null,
    outputPrice: row.pricing?.price_1m_output_tokens ?? null,
    speed: row.performance?.median_output_tokens_per_second ?? null }));
  await save(env, 'ai', { models, indexVersion: version, attribution: 'Artificial Analysis' });
  for (let start = 0; start < models.length; start += 30) {
    const batch = models.slice(start, start + 30);
    await env.DB.prepare('INSERT OR REPLACE INTO ai_ranks(day,model_id,rank) VALUES ' + batch.map(() => '(?,?,?)').join(','))
      .bind(...batch.flatMap(row => [today, row.id, row.rank])).run();
  }
}
