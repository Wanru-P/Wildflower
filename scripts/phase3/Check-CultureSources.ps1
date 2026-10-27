param([string]$DataDirectory = 'docs/Wildflower_Phase3_All_In_One/phase3_handoff/data')
$ErrorActionPreference = 'Stop'
$data = Get-Content -LiteralPath (Join-Path $DataDirectory 'culture_entries.json') -Raw | ConvertFrom-Json
$requests = foreach ($entry in $data.entries) {
    foreach ($work in $entry.literaryWorks) {
        if ($work.sourceUrl) { [pscustomobject]@{Key=$entry.speciesKey; Kind='literary'; Url=$work.sourceUrl; Expected=$work.excerpt} }
    }
    if ($entry.taxonomySource.url) { [pscustomobject]@{Key=$entry.speciesKey; Kind='taxonomy'; Url=$entry.taxonomySource.url; Expected=$entry.scientificName} }
}
# One bounded request per supplied URL, no circumvention or full-text archive.
$requests | ForEach-Object -ThrottleLimit 4 -Parallel {
    $request = $_
    try {
        $response = Invoke-WebRequest -Uri $request.Url -TimeoutSec 15 -Headers @{'User-Agent'='WildflowerLens-EditorialReview/0.1'}
        $plain = [System.Net.WebUtility]::HtmlDecode(($response.Content -replace '<[^>]+>', ' ')) -replace '\s+', ''
        try { $plain = [Microsoft.VisualBasic.Strings]::StrConv($plain, [Microsoft.VisualBasic.VbStrConv]::SimplifiedChinese, 2052) } catch {}
        $expected = $request.Expected -replace '\s+', ''
        [pscustomobject]@{key=$request.Key; kind=$request.Kind; url=$request.Url; status=[int]$response.StatusCode; textFound=$plain.Contains($expected); note='Literal text check only; not editorial or taxonomic approval'} | ConvertTo-Json -Compress
    } catch {
        [pscustomobject]@{key=$request.Key; kind=$request.Kind; url=$request.Url; status='unreviewed'; textFound=$false; note=$_.Exception.Message} | ConvertTo-Json -Compress
    }
}
