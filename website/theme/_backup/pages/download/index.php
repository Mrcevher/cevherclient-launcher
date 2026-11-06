<?php $this->layout('layouts/main'); ?>

<section class="section download-section">
	<div class="container">
		<div class="row">
			<div class="col-md-12">
				<?php $this->insert('components/breadcrumb', ['items' => [
					[
						"text" => t__("Download")
					]
				]]) ?>
			</div>
			<div class="col-md-12">
				<div class="card" data-aos="fade-up">
					<div class="card-header">
						<h2 class="card-title"><?php e__('Download'); ?></h2>
					</div>
					<?php if (count($files) > 0): ?>
						<div class="card-body p-0">
							<div class="table-responsive">
								<table class="table table-hover table-vcenter">
									<tbody>
									<?php foreach ($files as $file): ?>
										<tr>
											<td>
												<a href="<?php echo url('download.show', ['id' => $file["id"], 'slug' => $file["slug"]]) ?>" class="fw-medium d-flex align-items-center no-underline">
													<i class="ti ti-download me-2"></i>
													<?php echo $file["name"]; ?>
												</a>
											</td>
											<td class="text-end">
												<a href="<?php echo url('download.show', ['id' => $file["id"], 'slug' => $file["slug"]]) ?>" class="btn btn-primary">
													<?php e__('View') ?>
												</a>
											</td>
										</tr>
									<?php endforeach; ?>
									</tbody>
								</table>
							</div>
						</div>
					<?php else: ?>
						<div class="card-body">
							<?php $this->insert('components/alert', ['type' => 'error', 'message' => t__("No data were found!")]); ?>
						</div>
					<?php endif; ?>
				</div>
			</div>
		</div>
	</div>
</section>