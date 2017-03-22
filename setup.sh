DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export CTXP="$DIR"
export CDEP="$CTXP/deploy"
export CLOCK="$CTXP/lock"
export CLOG="$CTXP/log"

cd "$CTXP"
# The latest and greatest (as of 2017-03-22) command to start ctxp for use
# with the PubMed pumpkin project.
ctxp () {
  cd "$CTXP"
  mkdir -p "$CDEP" "$CLOCK" "$CLOG"
  java -Djetty.port=11999 \
       -Djava.io.tmpdir="$CTXP/jetty-temp-dir" \
       -Dxml.catalog.files=/pmc/load/catalog/linux-oxygen-pmc3-catalog.xml \
       -Dcache_ids=true \
       -Ditem_source=gov.ncbi.pmc.cite.PubmedPubOneItemSource \
       -Ditem_source_loc='http://pubone.linkerd.ncbi.nlm.nih.gov/pubone/pubmed_${id}' \
       -Dlog_level=DEBUG \
       -Dlog="$CLOG" \
       -Did_converter_url=https://dev.ncbi.nlm.nih.gov/pmc/utils/idconv/v1.0/ \
       -Dproxy=proxy.linkerd.service.bethesda-prod.consul.ncbi.nlm.nih.gov:4140 \
       -jar "$CTXP/target/pmc-citation-exporter-1.1.2.jar"
}
