/**
 * Server-side allowlist for association-football news.
 * Generic national news RSS sources are deliberately excluded at the feed list.
 * Mixed sports feeds must contain a specific football entity or competition.
 */
const NON_FOOTBALL_SPORTS = /basketball|handball|volleyball|tennis|futsal|formula\s?1|formula one|motorsport|cricket|boxing|\bufc\b|\bmma\b|wrestling|swimming|athletics|cycling|table tennis|rugby|squash|badminton|كرة السلة|كرة اليد|الكرة الطائرة|التنس|كرة الصالات|فورمولا 1|سباقات السيارات|كريكيت|ملاكمة|مصارعة|سباحة|ألعاب القوى|العاب القوي|دراجات هوائية|كرة الطاولة|الاسكواش|الريشة الطائرة/i;

const FOOTBALL_SIGNALS = /\bfootball\b|\bsoccer\b|\bfifa\b|\buefa\b|\bcaf\b|\bafc\b|premier league|la liga|serie a|bundesliga|ligue 1|saudi pro league|egyptian premier league|champions league|europa league|conference league|club world cup|world cup|africa cup of nations|كرة القدم|كره القدم|كرة قدم|كره قدم|كروي|الكروية|دوري أبطال أوروبا|دوري ابطال اوروبا|دوري أبطال أفريقيا|دوري ابطال افريقيا|الدوري الإنجليزي|الدوري الاسباني|الدوري الإسباني|الدوري الإيطالي|الدوري الالماني|الدوري الألماني|الدوري الفرنسي|الدوري السعودي|دوري روشن|الدوري المصري|كأس العالم للأندية|كاس العالم للانديه|الأهلي المصري|الاهلي المصري|الزمالك|بيراميدز|الهلال السعودي|النصر السعودي|الاتحاد السعودي|ليفربول|مانشستر سيتي|مانشستر يونايتد|أرسنال|ارسنال|تشيلسي|ريال مدريد|برشلونة|بايرن ميونخ|باريس سان جيرمان|يوفنتوس|انتر ميلان|ميلان الإيطالي|محمد صلاح|عمر مرموش|مبابي|ميسي|كريستيانو رونالدو|هالاند|لامين يامال|رياض محرز|أشرف حكيمي|\breal madrid\b|\bbarcelona\b|\bliverpool\b|\bmanchester city\b|\bmanchester united\b|\barsenal\b|\bchelsea\b|\bbayern munich\b|\bparis saint.germain\b|\bjuventus\b|\binter milan\b|\bmohamed salah\b|\bomar marmoush\b|\bkylian mbappe\b|\blionel messi\b|\berling haaland\b/i;

const DEDICATED_FOOTBALL_FEEDS = new Set([
  "btolat", "kooora", "bbc-football", "bbc-premier", "bbc-champions",
  "bbc-europe", "sky-football", "sky-premier", "guardian-football",
]);

export function isFootballNewsArticle(title, summary = "", feedId = "") {
  const content = `${String(title || "")} ${String(summary || "")}`.trim();
  if (!content) return false;
  if (NON_FOOTBALL_SPORTS.test(content)) return false;

  const id = String(feedId || "").toLowerCase();
  if (DEDICATED_FOOTBALL_FEEDS.has(id) || /^btolat-[a-z0-9-]+$/.test(id)) return true;

  // Mixed sports publishers (for example Hesport) must identify football
  // explicitly; generic words such as "match", "coach" or "goal" are not enough.
  return FOOTBALL_SIGNALS.test(content);
}
